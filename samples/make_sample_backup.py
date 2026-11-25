"""Tạo tệp sao lưu mẫu (định dạng healthapp-export-v1) cho một người dùng hư cấu, 21 ngày gần nhất.

Chạy lại trước buổi demo để ngày tháng luôn là "3 tuần gần đây":
    python samples/make_sample_backup.py
Kết quả: samples/sample-backup.json -> khôi phục trong app: Cài đặt -> Khôi phục từ bản xuất.
Mọi id bắt đầu bằng "bk_", nên khôi phục lại nhiều lần chỉ ghi đè, không nhân đôi.
"""
import json
import os
import random
from datetime import date, datetime, time, timedelta

DAYS = 21
ACC = "local_default_account"  # app chuyển mọi dòng sang tài khoản hiện tại khi khôi phục
rnd = random.Random(2026)
today = date.today()
now = datetime.now().replace(microsecond=0)
days = [today - timedelta(days=i) for i in range(DAYS - 1, -1, -1)]


def ts(d, h, m=0):
    return datetime.combine(d, time(h, m)).isoformat()


created = ts(days[0] - timedelta(days=1), 9)

# ---- Hồ sơ: nam, 28 tuổi, 170 cm, 68 kg, vận động vừa -> TDEE ≈ 2490 kcal (Mifflin–St Jeor × 1,55)
profile = {
    "id": "bk_profile", "accountId": ACC, "fullName": "Nguyễn Minh An",
    "dateOfBirth": f"{today.year - 28}-03-14", "gender": "MALE", "height": 170, "weight": 68.0,
    "activityLevel": "MODERATELY_ACTIVE", "targetCalories": 2490,
    "targetProtein": 108.8, "targetCarbs": 327.0, "targetFat": 83.0,
    "createdAt": created, "updatedAt": created,
}

# ---- Món ăn (giá trị cho 1 khẩu phần = servingSize gram)
DISHES = {
    "pho": ("Phở bò", 500, 450, 28, 55, 12),
    "banhmi": ("Bánh mì thịt", 200, 420, 18, 50, 15),
    "xoi": ("Xôi lạc", 200, 380, 9, 70, 7),
    "oat": ("Yến mạch sữa", 250, 220, 9, 34, 6),
    "egg": ("Trứng luộc", 50, 78, 6.3, 0.6, 5.3),
    "comtam": ("Cơm tấm sườn", 400, 650, 32, 80, 22),
    "buncha": ("Bún chả", 450, 550, 25, 60, 22),
    "comga": ("Cơm gà", 400, 580, 30, 70, 18),
    "rice": ("Cơm trắng", 200, 260, 5, 57, 0.6),
    "thitkho": ("Thịt kho trứng", 150, 330, 20, 6, 25),
    "cakho": ("Cá kho tộ", 150, 220, 25, 5, 11),
    "raumuong": ("Rau muống xào tỏi", 150, 110, 4, 7, 8),
    "canh": ("Canh chua", 300, 120, 9, 14, 3),
    "banana": ("Chuối", 120, 105, 1.3, 27, 0.4),
    "yogurt": ("Sữa chua", 100, 80, 4, 12, 2),
    "trasua": ("Trà sữa trân châu", 500, 400, 4, 70, 12),
}
foods = [{"id": f"bk_food_{k}", "name": n, "calories": kc, "protein": float(p), "carbohydrates": float(c),
          "fat": float(f), "fiber": 0.0, "sugar": 0.0, "sodium": 0, "servingSize": g, "source": "USER_CREATED"}
         for k, (n, g, kc, p, c, f) in DISHES.items()]

meals, items = [], []


def meal(d, mtype, hour, keys):
    mid = f"bk_meal_{d}_{mtype}"
    tot = [0, 0.0, 0.0, 0.0]
    for i, k in enumerate(keys):
        n, g, kc, p, c, f = DISHES[k]
        items.append({"id": f"bk_item_{d}_{mtype}_{i}", "mealId": mid, "foodId": f"bk_food_{k}", "quantity": 1.0,
                      "portionGrams": g, "calories": kc, "protein": float(p), "carbohydrates": float(c), "fat": float(f),
                      "fiber": 0.0, "sugar": 0.0, "sodium": 0})
        tot[0] += kc; tot[1] += p; tot[2] += c; tot[3] += f
    at = ts(d, hour, rnd.randint(0, 45))
    meals.append({"id": mid, "accountId": ACC, "mealType": mtype, "mealDate": str(d), "totalCalories": tot[0],
                  "totalProtein": round(tot[1], 1), "totalCarbs": round(tot[2], 1), "totalFat": round(tot[3], 1),
                  "totalFiber": 0.0, "totalSugar": 0.0, "totalSodium": 0, "notes": None, "imageUri": None,
                  "createdAt": at, "updatedAt": at})


water, sleep, activity, weight, vitals, moods = [], [], [], [], [], []
habit_defs = [("bk_habit_water", "Uống đủ 2 lít nước", 0.75), ("bk_habit_walk", "Đi bộ 30 phút", 0.65),
              ("bk_habit_read", "Đọc sách 15 phút", 0.5), ("bk_habit_sleep", "Ngủ trước 23:30", 0.55)]
habit_logs, med_logs = [], []
MOODS = ["Vui", "Bình yên", "Mệt", "Bình yên", "Vui", "Căng thẳng", "Bình yên", "Buồn"]

for i, d in enumerate(days):
    is_today = d == today
    weekend = d.weekday() >= 5
    # Bữa ăn (hôm nay chỉ đến bữa trưa nếu đã quá 12h, để màn hình thấy "ngày đang diễn ra")
    meal(d, "BREAKFAST", 7, [["pho"], ["banhmi"], ["oat", "egg"], ["xoi"]][i % 4])
    if not is_today or now.hour >= 12:
        meal(d, "LUNCH", 12, [["comtam"], ["buncha"], ["comga"]][i % 3])
    if not is_today:
        meal(d, "DINNER", 19, [["rice", "thitkho", "raumuong"], ["rice", "cakho", "canh"]][i % 2])
        if weekend:
            meal(d, "SNACK", 16, ["trasua"])
        elif i % 3 == 0:
            meal(d, "SNACK", 16, [["banana"], ["yogurt"]][i % 2])
    # Nước: 6–10 cốc 250 ml ngày cũ, hôm nay theo giờ hiện tại
    cups = min(max(0, (now.hour - 7) // 2), 5) if is_today else rnd.randint(6, 10)
    for g in range(cups):
        water.append({"id": f"bk_water_{d}_{g}", "accountId": ACC, "amountMl": 250,
                      "recordedAt": ts(d, 7 + g * 1 + g // 2, 10), "date": str(d)})
    # Giấc ngủ: đêm kết thúc vào ngày d; cuối tuần ngủ muộn/dậy muộn hơn
    bed = datetime.combine(d - timedelta(days=1), time(22, 30)) + timedelta(minutes=rnd.randint(0, 90) + (60 if weekend else 0))
    mins = rnd.randint(380, 470) + (40 if weekend else 0)
    sleep.append({"id": f"bk_sleep_{d}", "accountId": ACC, "bedtime": bed.isoformat(),
                  "wakeupTime": (bed + timedelta(minutes=mins)).isoformat(), "durationMinutes": mins,
                  "sleepQuality": rnd.randint(5, 9), "notes": None, "date": str(d)})
    # Vận động: bước chân mỗi ngày; chạy bộ thứ 3/5
    steps = 3500 if is_today else rnd.randint(5000, 11500)
    activity.append({"id": f"bk_steps_{d}", "accountId": ACC, "activityType": "WALKING", "durationMinutes": steps // 110,
                     "steps": steps, "distance": round(steps * 0.00075, 1), "caloriesBurned": round(steps * 0.04),
                     "intensity": "MODERATE", "recordedAt": now.isoformat() if is_today else ts(d, 21),
                     "date": str(d)})
    if d.weekday() in (1, 3) and not is_today:
        activity.append({"id": f"bk_run_{d}", "accountId": ACC, "activityType": "RUNNING", "durationMinutes": 30,
                         "steps": 0, "distance": 4.5, "caloriesBurned": 320, "intensity": "HIGH",
                         "recordedAt": ts(d, 18, 30), "date": str(d)})
    # Cân nặng 2 ngày/lần, giảm nhẹ
    if i % 2 == 0:
        weight.append({"id": f"bk_weight_{d}", "accountId": ACC,
                       "weight": round(69.2 - i * 0.06 + rnd.uniform(-0.3, 0.3), 1),
                       "recordedAt": ts(d, 6, 40), "date": str(d)})
    # Chỉ số: nhịp tim mỗi sáng, huyết áp 2 ngày/lần, đường huyết 2 lần/tuần
    vitals.append({"id": f"bk_hr_{d}", "accountId": ACC, "type": "HEART_RATE", "value": float(rnd.randint(60, 75)),
                   "secondaryValue": None, "unit": "bpm", "source": "MANUAL", "recordedAt": ts(d, 6, 50)})
    if i % 2 == 1:
        vitals.append({"id": f"bk_bp_{d}", "accountId": ACC, "type": "BLOOD_PRESSURE",
                       "value": float(rnd.randint(116, 128)), "secondaryValue": float(rnd.randint(74, 83)),
                       "unit": "mmHg", "source": "MANUAL", "recordedAt": ts(d, 6, 55)})
    if d.weekday() in (0, 4):
        vitals.append({"id": f"bk_glu_{d}", "accountId": ACC, "type": "GLUCOSE",
                       "value": round(rnd.uniform(4.8, 5.8), 1), "secondaryValue": None, "unit": "mmol/L",
                       "source": "MANUAL", "recordedAt": ts(d, 6, 45)})
    # Thói quen
    for h, (hid, _, rate) in enumerate(habit_defs):
        if (h == 0) if is_today else rnd.random() < rate:
            habit_logs.append({"id": f"bk_hlog_{hid}_{d}", "habitId": hid, "completionDate": str(d),
                               "status": True, "recordedAt": ts(d, 20, h)})
    # Thuốc: Vitamin D3 08:00; Omega-3 08:00 + 20:00 (chỉ các liều đã qua giờ)
    for mid, slot, rate in [("bk_med_vitd", "08:00", 0.92), ("bk_med_omega", "08:00", 0.85), ("bk_med_omega", "20:00", 0.75)]:
        if is_today and now.time() <= time.fromisoformat(slot):
            continue
        st = "TAKEN" if rnd.random() < rate else "SKIPPED"
        med_logs.append({"id": f"bk_medlog_{mid}_{d}_{slot.replace(':', '')}", "medicationId": mid, "date": str(d),
                         "time": slot, "status": st, "recordedAt": ts(d, int(slot[:2]), 5)})
    # Cảm xúc: các ngày trước (để check-in hôm nay khi demo). id = "<tài khoản>|<ngày>" như app.
    if not is_today:
        moods.append({"id": f"{ACC}|{d}", "accountId": ACC, "date": str(d), "mood": MOODS[i % len(MOODS)],
                      "stress": rnd.randint(2, 7), "note": "", "updatedAt": ts(d, 21, 30)})

habits = [{"id": hid, "accountId": ACC, "name": name, "description": None, "frequency": "DAILY", "daysOfWeek": None,
           "reminderTime": None, "goalValue": None, "goalUnit": None, "isEnabled": True,
           "createdAt": created, "updatedAt": created} for hid, name, _ in habit_defs]

medications = [
    {"id": "bk_med_vitd", "accountId": ACC, "name": "Vitamin D3", "dosage": "1000 IU", "instructions": "Sau bữa sáng",
     "times": "08:00", "daysOfWeek": None, "startDate": str(days[0]), "endDate": None, "isActive": True,
     "createdAt": created, "pillsLeft": 6, "pillsPerDose": 1},  # còn 6 viên -> app báo "sắp hết" (≤ 5 ngày sau 1 liều)
    {"id": "bk_med_omega", "accountId": ACC, "name": "Omega-3", "dosage": "1 viên", "instructions": "Uống cùng bữa ăn",
     "times": "08:00,20:00", "daysOfWeek": None, "startDate": str(days[0]), "endDate": None, "isActive": True,
     "createdAt": created, "pillsLeft": 40, "pillsPerDose": 1},
]

reminders = [
    {"id": "bk_rem_water", "accountId": ACC, "title": "Uống nước", "reminderType": "WATER", "reminderTime": "10:00",
     "isEnabled": True, "daysOfWeek": None, "createdAt": created},
    {"id": "bk_rem_walk", "accountId": ACC, "title": "Đi bộ buổi chiều", "reminderType": "EXERCISE",
     "reminderTime": "17:30", "isEnabled": True, "daysOfWeek": "MONDAY,WEDNESDAY,FRIDAY", "createdAt": created},
    {"id": "bk_rem_sleep", "accountId": ACC, "title": "Chuẩn bị đi ngủ", "reminderType": "SLEEP",
     "reminderTime": "23:00", "isEnabled": True, "daysOfWeek": None, "createdAt": created},
]

backup = {
    "exportedAt": now.isoformat(), "format": "healthapp-export-v1", "profile": profile,
    "habits": habits, "habitLogs": habit_logs, "meals": meals, "mealItems": items, "water": water,
    "waterGoalMl": 2200, "sleep": sleep, "activity": activity, "weight": weight, "vitals": vitals,
    "reminders": reminders, "medications": medications, "medicationLogs": med_logs, "moodCheckIns": moods,
    "foods": foods,
}
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sample-backup.json")
with open(out, "w", encoding="utf-8") as fh:
    json.dump(backup, fh, ensure_ascii=False, indent=2)
counts = {k: len(v) for k, v in backup.items() if isinstance(v, list)}
print(out)
print("rows:", sum(counts.values()) + 1, counts)
