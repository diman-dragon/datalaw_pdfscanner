// Native Ocr plugin (Tesseract) with a browser mock for dev.
const mock = {
  async ensure() {},
  async recognize({ page }) { return { text: `Mock OCR text, page ${page + 1}.\nТест распознавания.` }; },
  async release() {},
  async recognizeImage() { return { hocr: "<div class='ocr_page' title='bbox 0 0 100 100'><span class='ocrx_word' title='bbox 10 10 60 24; x_wconf 90'>Тест</span> <span class='ocrx_word' title='bbox 10 30 70 44; x_wconf 90'>Test123</span></div>" }; }
};
export const Ocr = window.Capacitor?.Plugins?.Ocr ?? mock;
