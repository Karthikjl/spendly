#!/usr/bin/env python3
"""Fill a Spendly database with made-up sample data for screenshots.

Usage: seed_demo_db.py path/to/expenses.db

The file must already have been created by the app (so Room's schema and identity hash are present). Everything written here
is invented: merchants are common brand names, people are fictional, and amounts are random. Nothing comes from a real phone.
"""
import random
import sqlite3
import sys
import time
from datetime import datetime, timedelta

if len(sys.argv) != 2:
    sys.exit(__doc__)

rng = random.Random(7)
now = datetime.now()
month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
prev_start = (month_start - timedelta(days=1)).replace(day=1)

# merchant, category, (min, max) rupees, how many times per month roughly
POOL = [
    ("SWIGGY", "Food", (180, 520)), ("ZOMATO", "Food", (220, 640)), ("CAFE BREW LAB", "Food", (120, 320)),
    ("BIGBASKET", "Groceries", (600, 1900)), ("BLINKIT", "Groceries", (150, 650)), ("FRESH MART", "Groceries", (300, 1200)),
    ("UBER", "Transport", (120, 420)), ("OLA", "Transport", (90, 360)), ("METRO CARD RECHARGE", "Transport", (200, 500)),
    ("AMAZON", "Shopping", (399, 2400)), ("MYNTRA", "Shopping", (799, 2200)),
    ("APOLLO PHARMACY", "Medical", (180, 760)),
    ("BOOKMYSHOW", "Entertainment", (300, 800)),
    ("RAHUL S", "Transfers", (500, 1400)), ("PRIYA K", "Transfers", (300, 900)),
]
FIXED = [  # day of month, merchant, category, rupees
    (3, "CITY POWER BOARD", "Bills", 1420), (4, "AIRTEL", "Bills", 599),
    (4, "NETFLIX", "Entertainment", 649), (5, "SPOTIFY", "Entertainment", 119),
    (12, "IRCTC", "Travel", 1240), (18, "REDBUS", "Travel", 880),
    (9, "PAWS & CLAWS VET", "Pets", 1150), (16, "PETSHOP", "Pets", 640),
]
COUNTS = {"Food": 15, "Groceries": 7, "Transport": 9, "Shopping": 3, "Medical": 3, "Entertainment": 1, "Transfers": 3}


def stamp(day_start, day, hour=None):
    when = day_start.replace(day=day, hour=hour if hour is not None else rng.randint(8, 22), minute=rng.randint(0, 59))
    return int(time.mktime(when.timetuple()) * 1000)


def month_rows(start, last_day, scale=1.0, tag="m"):
    rows = []
    n = 0

    def add(ts, merchant, category, rupees, debit=True):
        nonlocal n
        n += 1
        paise = int(round(rupees * scale)) * 100 if debit else rupees * 100
        rows.append((f"demo-{tag}-{n}", paise, 1 if debit else 0, merchant, category, "4821", ts))

    add(stamp(start, 1, 9), "ACME LABS SALARY", "Income", 62000, debit=False)
    if last_day >= 9:
        add(stamp(start, 9, 15), "DESIGN CLIENT PAYMENT", "Income", 8500, debit=False)
    for day, merchant, category, rupees in FIXED:
        if day <= last_day:
            add(stamp(start, day), merchant, category, rupees)
    for category, count in COUNTS.items():
        options = [p for p in POOL if p[1] == category]
        for _ in range(count):
            merchant, _, (low, high) = rng.choice(options)
            add(stamp(start, rng.randint(1, last_day)), merchant, category, rng.randint(low, high))
    # Make sure each of the last 7 days has some spending so the weekly chart looks alive.
    if start.month == now.month:
        for back in range(7):
            day = now.day - back
            if day >= 1 and back > 0:
                merchant, category, (low, high) = rng.choice([(m, c, r) for m, c, r in POOL if c in ("Food", "Transport", "Groceries")])
                add(stamp(start, day, rng.randint(8, 20)), merchant, category, rng.randint(low, high))
    # Never invent transactions that are in the future.
    return [r for r in rows if r[6] <= int(now.timestamp() * 1000)]


current = month_rows(month_start, now.day, 1.0, "cur")
spent_now = sum(r[1] for r in current if r[2])
previous = month_rows(prev_start, (month_start - timedelta(days=1)).day, 1.0, "prev")
spent_prev = sum(r[1] for r in previous if r[2])
# Last month was about 12% lower, so Home shows "12% more than last month".
factor = (spent_now / 1.12) / spent_prev
previous = [(k, int(round(p * factor / 100)) * 100 if d else p, d, m, c, a, t) for k, p, d, m, c, a, t in previous]

by_category = {}
for _, paise, debit, _, category, _, _ in current:
    if debit:
        by_category[category] = by_category.get(category, 0) + paise

# Budget used (%) per category: a mix of comfortable, nearly there and over.
USED = {"Food": 0.83, "Groceries": 0.60, "Transport": 1.12, "Shopping": 0.46, "Entertainment": 0.90,
        "Bills": 0.67, "Medical": 0.73, "Pets": 0.89}
budgets = []
for category, used in USED.items():
    spent = by_category.get(category, 0) / 100
    if spent:
        budgets.append((category, int(-(-spent / used // 100) * 100) * 100))

db = sqlite3.connect(sys.argv[1])
for table in ("txn", "merchant_rule", "budget", "custom_category"):
    db.execute(f"DELETE FROM {table}")
db.executemany("INSERT INTO txn (dedupeKey, amountPaise, isDebit, merchant, category, account, timestamp) VALUES (?,?,?,?,?,?,?)", current + previous)
db.executemany("INSERT INTO custom_category (name, colorArgb, iconKey) VALUES (?,?,?)",
               [("Travel", 0xFF3B9EFF, "flight"), ("Pets", 0xFFF5B301, "pets")])
db.executemany("INSERT INTO merchant_rule (merchant, category) VALUES (?,?)",
               [("IRCTC", "Travel"), ("REDBUS", "Travel"), ("PAWS & CLAWS VET", "Pets"), ("PETSHOP", "Pets")])
db.executemany("INSERT INTO budget (category, limitPaise) VALUES (?,?)", budgets)
db.commit()
db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
db.close()
print(f"{len(current)} transactions this month, {len(previous)} last month, {len(budgets)} budgets, 2 custom categories")
