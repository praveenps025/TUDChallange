import { useEffect, useRef, useState } from "react";
import jsQR from "jsqr";

const SCAN_EVERY_MS = 150;   // decoding every frame would drain phone batteries
const SAME_CODE_AFTER_MS = 3000; // ignore the same label held in view
const MAX_WIDTH = 640;       // decode a scaled-down frame; labels are large enough

const PROBLEMS = {
  insecure: "The camera only works over a secure connection. On a phone, open the https:// address (npm run dev:phone).",
  denied: "Camera access is blocked. Allow it in your browser's site settings, or type the ID below.",
  nocamera: "No camera was found on this device. Type the ID below.",
  failed: "The camera couldn't be started. Type the ID below.",
};

// Live QR scanning with the device's camera (the back camera on phones).
// Calls onScan with the text of each new code it reads.
export default function CameraScanner({ onScan, paused = false }) {
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const onScanRef = useRef(onScan);
  const pausedRef = useRef(paused);
  const [problem, setProblem] = useState(null);
  onScanRef.current = onScan;
  pausedRef.current = paused;

  useEffect(() => {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setProblem("insecure");
      return;
    }
    let stream;
    let frame;
    let stopped = false;
    let lastDecode = 0;
    let last = { text: null, at: 0 };

    function tick(time) {
      const video = videoRef.current;
      if (video && video.readyState >= 2 && !pausedRef.current && time - lastDecode >= SCAN_EVERY_MS) {
        lastDecode = time;
        const scale = Math.min(1, MAX_WIDTH / video.videoWidth);
        const width = Math.round(video.videoWidth * scale);
        const height = Math.round(video.videoHeight * scale);
        const canvas = canvasRef.current;
        canvas.width = width;
        canvas.height = height;
        const context = canvas.getContext("2d", { willReadFrequently: true });
        context.drawImage(video, 0, 0, width, height);
        const code = jsQR(context.getImageData(0, 0, width, height).data, width, height, { inversionAttempts: "dontInvert" });
        const text = code?.data?.trim();
        if (text && (text !== last.text || time - last.at > SAME_CODE_AFTER_MS)) {
          last = { text, at: time };
          navigator.vibrate?.(60);
          onScanRef.current(text);
        }
      }
      frame = requestAnimationFrame(tick);
    }

    navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" } }, audio: false })
      .then(media => {
        if (stopped) return media.getTracks().forEach(track => track.stop());
        stream = media;
        videoRef.current.srcObject = media;
        videoRef.current.play().catch(() => {});
        frame = requestAnimationFrame(tick);
      })
      .catch(error => setProblem(
        error.name === "NotAllowedError" ? "denied" : error.name === "NotFoundError" || error.name === "OverconstrainedError" ? "nocamera" : "failed"));

    // Leaving the screen turns the camera off
    return () => {
      stopped = true;
      cancelAnimationFrame(frame);
      stream?.getTracks().forEach(track => track.stop());
    };
  }, []);

  if (problem) {
    return (
      <div className="camera placeholder">
        <span className="frame" />
        <span className="hint">{PROBLEMS[problem]}</span>
      </div>
    );
  }

  return (
    <div className="camera live">
      <video ref={videoRef} muted playsInline aria-label="Camera view" />
      <canvas ref={canvasRef} hidden />
      <span className="frame" />
      <span className="hint">{paused ? "Checking…" : "Point the camera at the QR label"}</span>
    </div>
  );
}
