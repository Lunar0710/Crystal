// Small line charts for the live values: one canvas, a rolling series,
// a soft gradient under the line. No library; redrawn only when data changes.
class LineChart {
  constructor(canvas, { max = 100, color = '#8ea2ff', length = 60, grid = true, unit = '', second = null } = {}) {
    this.c = canvas; this.ctx = canvas.getContext('2d')
    this.max = max; this.color = color; this.length = length; this.grid = grid; this.unit = unit
    this.data = []; this.data2 = second ? [] : null; this.color2 = second
    this.resize()
    new ResizeObserver(() => { this.resize(); this.draw() }).observe(canvas)
  }
  resize() {
    const dpr = Math.min(2, window.devicePixelRatio || 1), r = this.c.getBoundingClientRect()
    this.w = Math.max(10, r.width); this.h = Math.max(10, r.height)
    this.c.width = this.w * dpr; this.c.height = this.h * dpr
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  }
  push(v, v2) {
    this.data.push(v); if (this.data.length > this.length) this.data.shift()
    if (this.data2) { this.data2.push(v2); if (this.data2.length > this.length) this.data2.shift() }
    this.draw()
  }
  top() {
    if (this.max !== 'auto') return this.max
    const all = this.data.concat(this.data2 || []).filter(v => v != null)
    const m = Math.max(1, ...all)
    const nice = [10, 20, 30, 50, 80, 100, 150, 200, 300, 500, 1000, 2000, 5000, 1e4, 2e4, 5e4, 1e5, 2e5, 5e5, 1e6, 2e6, 5e6, 1e7, 2e7, 5e7, 1e8, 1e9]
    return nice.find(n => n >= m * 1.15) || m * 1.2
  }
  line(series, color, top) {
    const { ctx, w, h } = this, n = this.length, pad = 4
    const x = i => (i + (n - series.length)) / (n - 1) * w
    const y = v => h - pad - (Math.min(v, top) / top) * (h - pad * 2)
    const pts = series.map((v, i) => v == null ? null : [x(i), y(v)])
    // Segments between gaps (a missing ping is a gap, not a zero).
    let seg = []
    const flush = () => {
      if (seg.length > 1) {
        ctx.beginPath(); ctx.moveTo(seg[0][0], seg[0][1])
        for (let i = 1; i < seg.length; i++) {
          const [px, py] = seg[i - 1], [cx, cy] = seg[i], mx = (px + cx) / 2
          ctx.bezierCurveTo(mx, py, mx, cy, cx, cy)
        }
        ctx.strokeStyle = color; ctx.lineWidth = 2; ctx.stroke()
        ctx.lineTo(seg[seg.length - 1][0], h); ctx.lineTo(seg[0][0], h); ctx.closePath()
        const g = ctx.createLinearGradient(0, 0, 0, h); g.addColorStop(0, color + '55'); g.addColorStop(1, color + '00')
        ctx.fillStyle = g; ctx.fill()
      }
      seg = []
    }
    pts.forEach(p => p ? seg.push(p) : flush()); flush()
    const last = pts[pts.length - 1]
    if (last) { ctx.fillStyle = color; ctx.shadowColor = color; ctx.shadowBlur = 12; ctx.beginPath(); ctx.arc(last[0] - 2, last[1], 3, 0, 7); ctx.fill(); ctx.shadowBlur = 0 }
    // Missing samples (lost pings) as red ticks at the bottom.
    series.forEach((v, i) => { if (v == null && this.markGaps) { ctx.fillStyle = '#e5484d'; ctx.fillRect(x(i) - 1, h - 6, 2, 6) } })
  }
  draw() {
    const { ctx, w, h } = this
    ctx.clearRect(0, 0, w, h)
    const top = this.top()
    if (this.grid) {
      ctx.strokeStyle = 'rgba(255,255,255,.05)'; ctx.lineWidth = 1
      for (let i = 1; i < 4; i++) { const y = Math.round(h * i / 4) + .5; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke() }
      ctx.fillStyle = 'rgba(255,255,255,.25)'; ctx.font = '10px Geist Mono, monospace'
      ctx.fillText(this.fmt ? this.fmt(top) : top + this.unit, 4, 12)
    }
    if (this.data2) this.line(this.data2, this.color2, top)
    this.line(this.data, this.color, top)
  }
}
window.LineChart = LineChart
