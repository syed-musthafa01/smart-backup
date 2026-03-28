import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder, MinMaxScaler
from pathlib import Path

# ===============================
# Paths
# ===============================
DATA_PATH = Path("data/smart_backup_phase1_dataset.csv")
TFLITE_PATH = Path("models/priority_model.tflite")

# ===============================
# Load Dataset
# ===============================
print("Loading dataset...")
data = pd.read_csv(DATA_PATH)

# ===============================
# Encode categorical features
# ===============================
print("Encoding categorical features...")
for col in data.select_dtypes(include=["object"]).columns:
    le = LabelEncoder()
    data[col] = le.fit_transform(data[col])

# ===============================
# Split features / target
# ===============================
TARGET_COLUMN = "backup_priority_score"   # or backup_priority_score
X = data.drop(TARGET_COLUMN, axis=1)
y = data[TARGET_COLUMN]

# ===============================
# Normalize numeric features (VERY IMPORTANT)
# ===============================
scaler = MinMaxScaler()
X = scaler.fit_transform(X)

# Save scaler values for Android
np.save("models/feature_min.npy", scaler.data_min_)
np.save("models/feature_max.npy", scaler.data_max_)

# ===============================
# Train-Test Split
# ===============================
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42
)

# ===============================
# Build Neural Network (Mobile Friendly)
# ===============================
model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(X.shape[1],)),
    tf.keras.layers.Dense(32, activation="relu"),
    tf.keras.layers.Dense(16, activation="relu"),
    tf.keras.layers.Dense(1, activation="linear")  # Regression
])

model.compile(
    optimizer="adam",
    loss="mse",
    metrics=["mae"]
)

# ===============================
# Train Model
# ===============================
print("Training TFLite-ready model...")
model.fit(
    X_train, y_train,
    epochs=30,
    batch_size=32,
    validation_split=0.1,
    verbose=1
)

# ===============================
# Convert to TFLite
# ===============================
print("Converting to TFLite...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]

tflite_model = converter.convert()

TFLITE_PATH.parent.mkdir(exist_ok=True)
with open(TFLITE_PATH, "wb") as f:
    f.write(tflite_model)

print("TFLite model saved successfully")
