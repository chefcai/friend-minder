#!/usr/bin/env python3
"""Reseed a debug emulator with realistic-scale, fictional Friend-Minder data.

FRM-150. Re-runnable: every run converges the device to the same seed.

What it does (emulator/debug builds only - it uses `run-as`):
  1. Makes sure every name in SEED_NAMES exists in the device Contacts
     provider with a 555 phone number (inserting only the missing ones;
     contacts it did not create are never modified or deleted).
  2. Force-stops the app and REPLACES its tracked-contact list, reminder
     counters, statistics cache, outreach logs, groups, memberships and
     special dates with the seed. Settings are left untouched.

Seed shape (what the Phase 3.1 audit needs):
  * 48 tracked contacts, including the long-name case "Donovan Arthen".
  * 6 groups, coloured with six different Phase 3 group-palette colours so
    the v2->v3 palette migration (FRM-175/177) has real rows to remap.
  * HEAVY_CONTACT has 34 outreaches (Contact Detail one-scroll, CD-2).
  * ZERO_CONTACT has 0 reminders sent and 0 outreaches.
  * Several contacts reached more than once this month (OH-1: events vs
    distinct people differ).

All names and numbers are fictional (555-01xx). No personal data.

Usage:
  python3 scripts/seed_emulator.py [--serial emulator-5554] [--dry-run]
"""
import argparse
import json
import random
import re
import subprocess
import sys
import time
from xml.sax.saxutils import escape

PKG = "com.example.friendminder"
DB = "databases/friend_minder.db"
DAY_MS = 24 * 60 * 60 * 1000

SEED_NAMES = [
    "Aaron Abbott", "Brenda Bishop", "Curtis Cole", "Donovan Arthen",
    "Elena Marsh", "Felix Grant", "Gloria Hayes", "Hector Ibarra",
    "Iris Jensen", "Jonah Keller", "Kira Lindqvist", "Leo Moreno",
    "Maya Okafor", "Nadia Petrova", "Oscar Quill", "Priya Raman",
    "Quentin Shaw", "Rosa Tanaka", "Silas Umber", "Tessa Voss",
    "Ulrich Weber", "Vivian Xiong", "Wes Yardley", "Xena Xu",
    "Yusuf Zaman", "Zoe Adler", "Amir Bashir", "Bianca Cruz",
    "Cyrus Dahl", "Delia Evans", "Emil Fischer", "Farah Gill",
    "Gideon Holt", "Hana Ito", "Isaac Juarez", "Jade Kowalski",
    "Kofi Mensah", "Lena Novak", "Marco Oliveira", "Nina Patel",
    "Omar Rashid", "Paula Santos", "Ravi Tiwari", "Sara Ulloa",
    "Theo Varga", "Una Walsh", "Victor Young", "Willa Zhou",
]
HEAVY_CONTACT = "Brenda Bishop"
HEAVY_OUTREACHES = 34
ZERO_CONTACT = "Xena Xu"

# (name, stored ARGB Int from the Phase 3 group palette, per-group interval)
GROUPS = [
    ("Family", -15498893, None),          # Teal      #138173
    ("College friends", -15424581, None),  # Cyan      #14A3BB
    ("Book club", -8141835, None),         # Sky       #83C3F5
    ("Work", -15505049, 14),               # Deep Teal #136967
    ("Neighbours", -3350295, None),        # Mist      #CCE0E9
    ("Climbing", -15390165, None),         # Midnight  #152A2B
]
GROUP_SIZES = [5, 12, 6, 9, 3, 1]
OUTREACH_TYPES = ["SMS", "SMS", "SMS", "CALL", "IN_PERSON", "VIDEO"]


class Adb:
    def __init__(self, serial, dry_run):
        self.serial = serial
        self.dry_run = dry_run

    def run(self, *args, stdin=None, mutate=False):
        cmd = ["adb", "-s", self.serial, *args]
        if mutate and self.dry_run:
            print("DRY-RUN:", " ".join(cmd))
            return ""
        res = subprocess.run(cmd, input=stdin, capture_output=True, text=True)
        if res.returncode != 0:
            sys.exit(f"adb failed: {' '.join(cmd)}\n{res.stderr}")
        return res.stdout

    def shell(self, command, mutate=False):
        return self.run("shell", command, mutate=mutate)

    def write_app_file(self, path, content):
        self.run("shell", f"run-as {PKG} sh -c 'cat > {path}'", stdin=content, mutate=True)


def phone_contacts(adb):
    """contact_id, display_name and number for every phone row on the device."""
    out = adb.shell(
        "content query --uri content://com.android.contacts/data/phones "
        "--projection contact_id:display_name:data1"
    )
    found = {}
    for m in re.finditer(r"contact_id=(\d+), display_name=(.*?), data1=(.*)$", out, re.M):
        found.setdefault(m.group(2).strip(), (m.group(1), m.group(3).strip()))
    return found


def raw_contact_ids(adb):
    """display_name -> raw contact _id, for live raw contacts."""
    out = adb.shell("content query --uri content://com.android.contacts/raw_contacts "
                    "--projection _id:display_name --where deleted=0")
    return {m.group(2).strip(): m.group(1)
            for m in re.finditer(r"_id=(\d+), display_name=(.*)$", out, re.M)}


def nameless_raw_ids(adb):
    """Raw contacts an interrupted run created before writing their name."""
    out = adb.shell("content query --uri content://com.android.contacts/raw_contacts "
                    "--projection _id:display_name --where deleted=0")
    return [m.group(1) for m in re.finditer(r"_id=(\d+), display_name=NULL$", out, re.M)]


def ensure_contacts(adb):
    """Insert the seed names that have no phone row yet.

    Every `content` call reads from /dev/null: it would otherwise swallow the
    rest of the script from stdin. Runs as ONE device-side shell script so it is a single adb round trip.
    A raw contact left half-created by an interrupted run (name, no phone)
    is completed rather than duplicated.
    """
    existing = phone_contacts(adb)
    missing = [n for n in SEED_NAMES if n not in existing]
    if missing:
        half_done = raw_contact_ids(adb)
        nameless = nameless_raw_ids(adb)
        uri = "content://com.android.contacts"
        lines = []
        for name in missing:
            number = f"555010{SEED_NAMES.index(name):02d}"
            if name in half_done:
                lines.append(f"id={half_done[name]}")
            else:
                if nameless:
                    lines.append(f"id={nameless.pop(0)}")
                else:
                    lines.append(f"content insert --uri {uri}/raw_contacts "
                                 "--bind account_type:n: --bind account_name:n: </dev/null")
                    lines.append(f"id=$(content query --uri {uri}/raw_contacts --projection _id "
                                 "--sort '_id DESC' </dev/null | head -1 | sed 's/.*_id=//')")
                lines.append(f"content insert --uri {uri}/data --bind raw_contact_id:i:$id "
                             "--bind mimetype:s:vnd.android.cursor.item/name "
                             f"--bind data1:s:'{name}' </dev/null")
            lines.append(f"content insert --uri {uri}/data --bind raw_contact_id:i:$id "
                         "--bind mimetype:s:vnd.android.cursor.item/phone_v2 "
                         f"--bind data1:s:{number} --bind data2:i:2 </dev/null")
        adb.run("shell", "sh -s", stdin="\n".join(lines) + "\n", mutate=True)
    print(f"contacts: {len(SEED_NAMES) - len(missing)} already present, {len(missing)} inserted")
    return existing if adb.dry_run else phone_contacts(adb)


def build_seed(contacts, now):
    rng = random.Random(150)
    tracked = [{"id": contacts[n][0], "name": n, "phoneNumber": contacts[n][1]} for n in SEED_NAMES]
    ids = {c["name"]: c["id"] for c in tracked}
    logs, counts, last = [], {}, {}

    def add_log(cid, ts):
        logs.append((f"seed-o-{cid}-{len(logs)}", cid, ts, rng.choice(OUTREACH_TYPES)))

    for c in tracked:
        cid, name = c["id"], c["name"]
        if name == ZERO_CONTACT:
            counts[cid] = 0
            continue
        if name == HEAVY_CONTACT:
            n, spacing = HEAVY_OUTREACHES, 5
        else:
            n, spacing = rng.randint(0, 8), rng.randint(3, 21)
        start = rng.randint(0, 40)
        for k in range(n):
            ts = now - (start + k * spacing) * DAY_MS - rng.randint(0, 10 * 3600) * 1000
            add_log(cid, ts)
            last[cid] = max(last.get(cid, 0), ts)
        counts[cid] = n + rng.randint(0, 4) if n else rng.randint(1, 3)
    # OH-1: a few people reached twice in the current month.
    for name in ["Aaron Abbott", "Donovan Arthen", "Maya Okafor"]:
        cid = ids[name]
        add_log(cid, now - 1 * DAY_MS)
        add_log(cid, now - 2 * DAY_MS)
        counts[cid] += 2
        last[cid] = max(last.get(cid, 0), now - DAY_MS)

    pool = [c["id"] for c in tracked if c["name"] != ZERO_CONTACT]
    groups, members = [], []
    for i, ((gname, color, interval), size) in enumerate(zip(GROUPS, GROUP_SIZES)):
        gid = f"seed-g-{i + 1}"
        groups.append((gid, gname, color, now - (60 - i) * DAY_MS, interval))
        members += [(cid, gid) for cid in rng.sample(pool, size)]
    members.append((ids["Donovan Arthen"], "seed-g-1"))
    members = sorted(set(members))

    dates = [
        (f"seed-d-{ids[HEAVY_CONTACT]}-1", ids[HEAVY_CONTACT], "Birthday", 11, 4, -7),
        (f"seed-d-{ids[HEAVY_CONTACT]}-2", ids[HEAVY_CONTACT], "Anniversary", 6, 18, 0),
        (f"seed-d-{ids[HEAVY_CONTACT]}-3", ids[HEAVY_CONTACT], "Graduation", 5, 30, -1),
        (f"seed-d-{ids['Donovan Arthen']}-1", ids["Donovan Arthen"], "Birthday", 2, 29, 0),
        (f"seed-d-{ids['Priya Raman']}-1", ids["Priya Raman"], "Birthday", 10, 2, -3),
    ]
    return tracked, logs, counts, last, groups, members, dates


def sql_str(value):
    return "NULL" if value is None else "'" + str(value).replace("'", "''") + "'"


def build_sql(logs, groups, members, dates):
    out = ["PRAGMA foreign_keys=ON;", "BEGIN;",
           "DELETE FROM contact_group_membership;", "DELETE FROM contact_groups;",
           "DELETE FROM outreach_logs;", "DELETE FROM special_dates;"]
    out += [f"INSERT INTO outreach_logs VALUES({sql_str(i)},{sql_str(c)},{ts},{sql_str(t)},NULL);"
            for i, c, ts, t in logs]
    out += [f"INSERT INTO contact_groups VALUES({sql_str(g)},{sql_str(n)},{col},NULL,{ts},{sql_str(iv)});"
            for g, n, col, ts, iv in groups]
    out += [f"INSERT INTO contact_group_membership VALUES({sql_str(c)},{sql_str(g)});" for c, g in members]
    out += [f"INSERT INTO special_dates VALUES({sql_str(i)},{sql_str(c)},{sql_str(l)},{m},{d},{r},'CUSTOM');"
            for i, c, l, m, d, r in dates]
    out += ["COMMIT;", "PRAGMA wal_checkpoint(TRUNCATE);"]
    return "\n".join(out) + "\n"


def prefs_xml(entries):
    body = "".join(f"    {e}\n" for e in entries)
    return f"<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n{body}</map>\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    adb = Adb(args.serial, args.dry_run)

    contacts = ensure_contacts(adb)
    if args.dry_run and any(n not in contacts for n in SEED_NAMES):
        contacts = {n: (str(1000 + i), f"555010{i:02d}") for i, n in enumerate(SEED_NAMES)}
    now = int(time.time() * 1000)
    tracked, logs, counts, last, groups, members, dates = build_seed(contacts, now)

    adb.shell(f"am force-stop {PKG}", mutate=True)
    friend_json = json.dumps(tracked, separators=(",", ":"), ensure_ascii=False)
    adb.write_app_file("shared_prefs/friend_minder_prefs.xml", prefs_xml(
        [f'<string name="friend_list_json">{escape(friend_json, {chr(34): "&quot;"})}</string>']))
    cooldown = [f'<int name="reminder_count_{cid}" value="{n}" />' for cid, n in counts.items()]
    cooldown += [f'<long name="last_suggested_{cid}" value="{ts}" />' for cid, ts in last.items()]
    adb.write_app_file("shared_prefs/friend_minder_cooldowns.xml", prefs_xml(cooldown))
    adb.write_app_file("shared_prefs/friend_minder_statistics_cache.xml", prefs_xml([]))
    adb.run("shell", f"run-as {PKG} sqlite3 {DB}", stdin=build_sql(logs, groups, members, dates), mutate=True)

    heavy = sum(1 for _, c, _, _ in logs if c == contacts[HEAVY_CONTACT][0])
    print(f"tracked={len(tracked)} groups={len(groups)} memberships={len(members)} "
          f"outreaches={len(logs)} heavy({HEAVY_CONTACT})={heavy} "
          f"zero-reminder({ZERO_CONTACT})={counts[contacts[ZERO_CONTACT][0]]} special_dates={len(dates)}")


if __name__ == "__main__":
    main()
