// The A4 sheet of the Scan tab (mm). Safe for a Web Worker.
export const SW = 210, SH = 297; // the sheet
export const GAP = 12; // gap between the items on a sheet
const MARGIN = 10; // free edge when items stand in a row

/** How many items of width w (mm) stand side by side on one sheet (a receipt shot in parts). */
export const rowCap = (w) => Math.max(1, Math.floor((SW - 2 * MARGIN + GAP) / (w + GAP)));
