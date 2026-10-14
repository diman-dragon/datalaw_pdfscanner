// Native Ocr plugin (Tesseract) with a browser mock for dev.
const mock = {
  async ensure() {},
  async recognize({ page }) { return { text: `Mock OCR text, page ${page + 1}.\nТест распознавания.` }; },
  async release() {},
};
export const Ocr = window.Capacitor?.Plugins?.Ocr ?? mock;
