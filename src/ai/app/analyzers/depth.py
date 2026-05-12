"""``DepthEstimator`` — MiDaS small monocular depth wrapper.

MiDaS outputs *relative inverse depth* (larger value = closer to camera) — there
is no metric scale. We expose the raw normalised map (0..1) so callers can
apply a scale factor from a reference object of known real-world size.

Lazy-loads ``MiDaS_small`` once per process via ``torch.hub``. First load
downloads ~80MB of weights into ``~/.cache/torch/hub``.
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import cv2
import numpy as np

from app.errors import ImageNotFoundError, ImageReadError

log = logging.getLogger("depth-estimator")


@dataclass(frozen=True)
class DepthResult:
    """Normalised inverse-depth map; values in [0, 1] (larger = closer)."""

    depth_map: np.ndarray   # shape (H, W), float32
    image_width: int
    image_height: int


_MODEL = None
_TRANSFORM = None
_DEVICE = None
_LOCK = threading.Lock()
_MODEL_TYPE = "MiDaS_small"


def _get_model():
    global _MODEL, _TRANSFORM, _DEVICE
    if _MODEL is not None:
        return _MODEL, _TRANSFORM, _DEVICE
    with _LOCK:
        if _MODEL is None:
            import torch
            import torch.hub as hub
            # MiDaS_small pulls a second hub.load("rwightman/gen-efficientnet-pytorch")
            # internally without forwarding trust_repo=True, which hangs uvicorn
            # on the y/N stdin prompt. Disable both trust checks globally and
            # also stub builtins.input so any remaining prompt auto-answers "y".
            hub._validate_not_a_forked_repo = lambda *a, **kw: None
            hub._check_repo_is_trusted = lambda *a, **kw: None
            import builtins
            builtins.input = lambda *a, **kw: "y"
            log.info("loading MiDaS depth model type=%s", _MODEL_TYPE)
            _DEVICE = "cpu"
            _MODEL = torch.hub.load("intel-isl/MiDaS", _MODEL_TYPE, trust_repo=True)
            _MODEL.to(_DEVICE).eval()
            tfm = torch.hub.load("intel-isl/MiDaS", "transforms", trust_repo=True)
            _TRANSFORM = tfm.small_transform
            log.info("MiDaS ready")
    return _MODEL, _TRANSFORM, _DEVICE


class DepthEstimator:
    """Run MiDaS_small and return a normalised relative-depth map."""

    def estimate(self, image_path: Path) -> DepthResult:
        if not image_path.exists() or not image_path.is_file():
            raise ImageNotFoundError(f"Resolved path does not exist: {image_path.name}")

        buf = np.fromfile(str(image_path), dtype=np.uint8)
        if buf.size == 0:
            raise ImageReadError(f"File is empty: {image_path.name}")
        img_bgr = cv2.imdecode(buf, cv2.IMREAD_COLOR)
        if img_bgr is None:
            raise ImageReadError(f"cv2.imdecode failed: {image_path.name}")
        img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
        h, w = img_rgb.shape[:2]

        import torch
        model, transform, device = _get_model()
        batch = transform(img_rgb).to(device)
        with torch.no_grad():
            pred = model(batch)
            pred = torch.nn.functional.interpolate(
                pred.unsqueeze(1),
                size=(h, w),
                mode="bicubic",
                align_corners=False,
            ).squeeze()

        depth = pred.cpu().numpy().astype(np.float32)
        d_min, d_max = float(depth.min()), float(depth.max())
        if d_max - d_min > 1e-6:
            depth = (depth - d_min) / (d_max - d_min)
        else:
            depth = np.zeros_like(depth)

        return DepthResult(depth_map=depth, image_width=int(w), image_height=int(h))


_SINGLETON: Optional[DepthEstimator] = None


def default_depth_estimator() -> DepthEstimator:
    global _SINGLETON
    if _SINGLETON is None:
        _SINGLETON = DepthEstimator()
    return _SINGLETON
