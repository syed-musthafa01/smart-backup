import pandas as pd
import joblib
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.preprocessing import LabelEncoder
from pathlib import Path

# ===============================
# Paths
# ===============================
DATA_PATH = Path("data/smart_backup_phase1_dataset.csv")
MODEL_PATH = Path("models/smart_backup_priority_model.pkl")

# ===============================
# Load Dataset
# ===============================
print("Loading dataset...")
data = pd.read_csv(DATA_PATH)

# ===============================
# Encode Categorical Columns
# ===============================
print("Encoding categorical features...")
label_encoders = {}

for col in data.select_dtypes(include=["object"]).columns:
    le = LabelEncoder()
    data[col] = le.fit_transform(data[col])
    label_encoders[col] = le

# ===============================
# Split Features / Target
# ===============================
TARGET_COLUMN = "backup_priority_score"  # or backup_priority_score (use correct name)

X = data.drop(TARGET_COLUMN, axis=1)
y = data[TARGET_COLUMN]

# ===============================
# Train-Test Split
# ===============================
print("Splitting dataset...")
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42
)

# ===============================
# Train Model
# ===============================
print("Training model...")
model = RandomForestRegressor(
    n_estimators=200,
    max_depth=12,
    min_samples_leaf=3,
    random_state=42,
    n_jobs=-1
)

model.fit(X_train, y_train)

# ===============================
# Evaluate Model
# ===============================
print("Evaluating model...")
y_pred = model.predict(X_test)

mae = mean_absolute_error(y_test, y_pred)
r2 = r2_score(y_test, y_pred)

print("\nModel Performance")
print("-----------------")
print(f"MAE: {mae:.2f}")
print(f"R² Score: {r2:.2f}")

# ===============================
# Save Model & Encoders
# ===============================
MODEL_PATH.parent.mkdir(exist_ok=True)
joblib.dump(model, MODEL_PATH)
joblib.dump(label_encoders, "models/label_encoders.pkl")

print("\nModel and encoders saved successfully")
