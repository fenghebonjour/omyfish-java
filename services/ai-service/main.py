import os
from fastapi import FastAPI
from pydantic import BaseModel
from typing import List

app = FastAPI(title="OMyFish AI Service")


class PredictionResult(BaseModel):
    scientific_name: str
    common_name: str
    confidence: float
    rank: int


class PredictionResponse(BaseModel):
    predictions: List[PredictionResult]
    model_version: str = "stub"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict", response_model=PredictionResponse)
async def predict(image_key: str, top_k: int = 5):
    stub_predictions = [
        PredictionResult(scientific_name="Micropterus salmoides", common_name="Largemouth Bass", confidence=0.92, rank=1),
        PredictionResult(scientific_name="Lepomis macrochirus", common_name="Bluegill", confidence=0.05, rank=2),
        PredictionResult(scientific_name="Pomoxis nigromaculatus", common_name="Black Crappie", confidence=0.02, rank=3),
        PredictionResult(scientific_name="Ictalurus punctatus", common_name="Channel Catfish", confidence=0.005, rank=4),
        PredictionResult(scientific_name="Sander vitreus", common_name="Walleye", confidence=0.005, rank=5),
    ]
    return PredictionResponse(predictions=stub_predictions[:top_k])
