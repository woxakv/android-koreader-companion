package io.github.woxakv.koreadercompanion.presentation.widget

// 26 weeks x 7 days: the closest whole-week multiple to 6 months (182,
// not 180 or 183 — the renderer's column model requires a multiple of 7).
const val CALENDAR_GRID_GRAPH_DAYS = 182
const val CALENDAR_GRID_GRAPH_CELL_SIZE_PX = 16
const val CALENDAR_GRID_GRAPH_CELL_GAP_PX = 2 // vertical-only gap between rows
const val CALENDAR_GRID_GRAPH_CELL_GAP_HORIZONTAL_PX = 8 // horizontal-only gap between columns
