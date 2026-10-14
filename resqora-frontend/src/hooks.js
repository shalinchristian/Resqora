import { useEffect, useState } from 'react'

// Counts from 0 up to the target number, used for scores.
export function useCountUp(target, ms = 300) {
  const [value, setValue] = useState(0)
  useEffect(() => {
    let frame
    const start = performance.now()
    const tick = (now) => {
      const t = Math.min((now - start) / ms, 1)
      setValue(Math.round(target * (1 - Math.pow(1 - t, 3))))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, ms])
  return value
}

// Becomes true right after the first render so CSS transitions can run.
export function useMounted() {
  const [mounted, setMounted] = useState(false)
  useEffect(() => { const id = requestAnimationFrame(() => setMounted(true)); return () => cancelAnimationFrame(id) }, [])
  return mounted
}
