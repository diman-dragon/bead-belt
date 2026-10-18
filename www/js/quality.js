// Адаптивное разрешение: если кадры стабильно медленнее порога, множитель разрешения холста снижается.
// Без DOM — проверяется в Node.
export class AdaptiveQuality {
  constructor({ min = 0.6, slowFrame = 1 / 38, warmFrames = 90, step = 0.82 } = {}) {
    this.min = min;
    this.slowFrame = slowFrame;
    this.warmFrames = warmFrames;
    this.step = step;
    this.value = 1;
    this.ema = 1 / 60;   // сглаженная длительность кадра
    this.warm = 0;       // кадры «прогрева» после старта/смены качества — их не считаем
  }

  // Возвращает true, если качество только что снизилось (нужно пересоздать холст).
  sample(dt) {
    if (++this.warm < this.warmFrames) return false;
    this.ema = this.ema * 0.96 + dt * 0.04;
    if (this.ema <= this.slowFrame || this.value <= this.min) return false;
    this.value = Math.max(this.min, this.value * this.step);
    this.ema = 1 / 60;
    this.warm = 0;
    return true;
  }
}
