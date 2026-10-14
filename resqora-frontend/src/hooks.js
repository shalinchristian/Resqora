import { useEffect, useState } from 'react'

// counts up from 0 to the target, used for scores
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

// true right after the first render so css transitions can run
export function useMounted() {
  const [mounted, setMounted] = useState(false)
  useEffect(() => { const id = requestAnimationFrame(() => setMounted(true)); return () => cancelAnimationFrame(id) }, [])
  return mounted
}
