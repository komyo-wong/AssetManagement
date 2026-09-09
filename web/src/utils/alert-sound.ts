/**
 * In-app alert sound. Prefer user custom mp3/wma; fall back to synthesized beep.
 * Browsers block autoplay until a user gesture unlocks AudioContext.
 */
let sharedCtx: AudioContext | null = null
let unlockBound = false
let cachedSoundUrl: string | null = null
let cachedSoundUserId: string | null = null
let customAudio: HTMLAudioElement | null = null
let looping = false
let beepLoopTimer: ReturnType<typeof setInterval> | null = null
let stopOnGestureBound = false

function getAudioContextCtor(): typeof AudioContext | null {
  return (
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext ||
    null
  )
}

function ensureContext(): AudioContext | null {
  const Ctor = getAudioContextCtor()
  if (!Ctor) return null
  if (!sharedCtx || sharedCtx.state === 'closed') {
    sharedCtx = new Ctor()
  }
  return sharedCtx
}

async function resumeContext(ctx: AudioContext) {
  if (ctx.state === 'suspended') {
    try {
      await ctx.resume()
    } catch {
      // ignored
    }
  }
}

function bindUnlockOnce() {
  if (unlockBound || typeof window === 'undefined') return
  unlockBound = true
  const unlock = () => {
    const ctx = ensureContext()
    if (ctx) void resumeContext(ctx)
  }
  window.addEventListener('pointerdown', unlock, { capture: true, passive: true })
  window.addEventListener('keydown', unlock, { capture: true, passive: true })
  window.addEventListener('touchstart', unlock, { capture: true, passive: true })
}

/** Call early so the first real alert can make sound after any click on the page. */
export function prepareAlertAudio() {
  bindUnlockOnce()
  ensureContext()
}

export function setCustomAlertSound(dataUrl: string | null, userId?: string | null) {
  cachedSoundUrl = dataUrl && dataUrl.trim() ? dataUrl.trim() : null
  cachedSoundUserId = userId || null
  if (customAudio) {
    customAudio.pause()
    customAudio = null
  }
}

export function clearCustomAlertSoundCache() {
  setCustomAlertSound(null, null)
}

function clearBeepLoopTimer() {
  if (beepLoopTimer) {
    clearInterval(beepLoopTimer)
    beepLoopTimer = null
  }
}

function unbindStopOnGesture() {
  if (!stopOnGestureBound || typeof window === 'undefined') return
  stopOnGestureBound = false
  window.removeEventListener('pointerdown', stopAlertSoundOnGesture, true)
  window.removeEventListener('keydown', stopAlertSoundOnGesture, true)
}

function stopAlertSoundOnGesture() {
  stopAlertSound()
}

/** Stop looping / current alert playback. */
export function stopAlertSound() {
  looping = false
  clearBeepLoopTimer()
  unbindStopOnGesture()
  if (customAudio) {
    try {
      customAudio.pause()
      customAudio.loop = false
      customAudio.currentTime = 0
    } catch {
      // ignored
    }
  }
}

/**
 * When sound loops without a dismiss UI, stop on the next user gesture.
 * Toast UI should call stopAlertSound() explicitly instead.
 */
export function stopAlertSoundOnNextGesture() {
  if (typeof window === 'undefined' || stopOnGestureBound) return
  stopOnGestureBound = true
  window.addEventListener('pointerdown', stopAlertSoundOnGesture, true)
  window.addEventListener('keydown', stopAlertSoundOnGesture, true)
}

function playOscillatorPattern(ctx: AudioContext) {
  const now = ctx.currentTime
  const pattern: Array<{ freq: number; at: number; dur: number }> = [
    { freq: 880, at: 0, dur: 0.16 },
    { freq: 1174, at: 0.18, dur: 0.16 },
    { freq: 880, at: 0.38, dur: 0.16 },
    { freq: 1318, at: 0.58, dur: 0.22 }
  ]
  for (const tone of pattern) {
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'square'
    osc.frequency.setValueAtTime(tone.freq, now + tone.at)
    const start = now + tone.at
    const end = start + tone.dur
    gain.gain.setValueAtTime(0.0001, start)
    gain.gain.exponentialRampToValueAtTime(0.22, start + 0.015)
    gain.gain.exponentialRampToValueAtTime(0.0001, end)
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start(start)
    osc.stop(end + 0.02)
  }
}

function playWavFallback(): Promise<boolean> {
  const sampleRate = 16000
  const seconds = 0.28
  const samples = Math.floor(sampleRate * seconds)
  const dataSize = samples
  const buffer = new ArrayBuffer(44 + dataSize)
  const view = new DataView(buffer)
  const writeStr = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i))
  }
  writeStr(0, 'RIFF')
  view.setUint32(4, 36 + dataSize, true)
  writeStr(8, 'WAVE')
  writeStr(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate, true)
  view.setUint16(32, 1, true)
  view.setUint16(34, 8, true)
  writeStr(36, 'data')
  view.setUint32(40, dataSize, true)
  for (let i = 0; i < samples; i++) {
    const t = i / sampleRate
    const envelope = Math.min(1, i / 400) * Math.min(1, (samples - i) / 800)
    const sample = Math.sin(2 * Math.PI * 980 * t) * envelope
    view.setUint8(44 + i, Math.max(0, Math.min(255, Math.floor(sample * 100 + 128))))
  }
  const blob = new Blob([buffer], { type: 'audio/wav' })
  const url = URL.createObjectURL(blob)
  const audio = new Audio(url)
  audio.volume = 0.85
  return audio
    .play()
    .then(() => true)
    .catch(() => false)
    .finally(() => {
      URL.revokeObjectURL(url)
    })
}

async function playCustomSound(dataUrl: string, loop: boolean): Promise<boolean> {
  try {
    if (customAudio) {
      customAudio.pause()
      customAudio = null
    }
    customAudio = new Audio(dataUrl)
    customAudio.volume = 0.9
    customAudio.loop = loop
    await customAudio.play()
    return true
  } catch {
    return false
  }
}

async function playDefaultBeep(): Promise<boolean> {
  const ctx = ensureContext()
  if (ctx) {
    await resumeContext(ctx)
    if (ctx.state === 'running') {
      try {
        playOscillatorPattern(ctx)
        return true
      } catch {
        // fall through
      }
    }
  }
  try {
    return await playWavFallback()
  } catch {
    return false
  }
}

async function playOnce(custom: string | null): Promise<boolean> {
  if (custom) {
    const ok = await playCustomSound(custom, false)
    if (ok) return true
  }
  return playDefaultBeep()
}

async function startLoop(custom: string | null): Promise<boolean> {
  stopAlertSound()
  looping = true
  if (custom) {
    const ok = await playCustomSound(custom, true)
    if (ok) return true
    // WMA often unsupported — fall back to default beep loop
  }
  const ok = await playDefaultBeep()
  if (!ok || !looping) {
    looping = false
    return ok
  }
  clearBeepLoopTimer()
  beepLoopTimer = setInterval(() => {
    if (!looping) return
    void playDefaultBeep()
  }, 1200)
  return true
}

export async function playAlertSound(options?: {
  customSoundUrl?: string | null
  /** Keep playing until stopAlertSound() */
  loop?: boolean
}): Promise<boolean> {
  prepareAlertAudio()
  const custom = options?.customSoundUrl ?? cachedSoundUrl
  if (options?.loop) {
    return startLoop(custom)
  }
  return playOnce(custom)
}

export function getCachedAlertSoundUserId() {
  return cachedSoundUserId
}

export function isAlertSoundLooping() {
  return looping
}
