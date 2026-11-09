// Builder inputs: bullet rows for multi-line lists, and a skills field with local suggestions.
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { suggestSkills } from './skills.js'
import { splitList } from './model.js'

const strip = (l) => l.replace(/^\s*[•\-*–·▪●]\s*/, '')

// textarea that grows with its text, so long bullets wrap instead of scrolling
function AutoGrow({ value, inputRef, className = '', ...props }) {
  const local = useRef(null)
  const ref = inputRef || local
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${el.scrollHeight}px`
  }, [value, ref])
  return <textarea ref={ref} rows={1} value={value} className={`resize-none overflow-hidden ${className}`} {...props} />
}

/**
 * One row per line. The value stays a newline-joined string, so the draft and the
 * export payload don't change shape. Enter adds a row, Backspace on an empty row
 * removes it, and pasting several lines splits them into rows.
 */
export function BulletList({ label, value, onChange, hint, placeholder = 'Add a point', addLabel = 'Add bullet', max = 20, maxLength = 200 }) {
  const rows = (value || '').split('\n')
  const items = rows.length ? rows : ['']
  const refs = useRef([])
  const [focusAt, setFocusAt] = useState(null)

  useEffect(() => {
    if (focusAt == null) return
    const el = refs.current[focusAt.i]
    if (el) {
      el.focus()
      const pos = focusAt.end ? el.value.length : 0
      el.setSelectionRange(pos, pos)
    }
    setFocusAt(null)
  }, [focusAt])

  const commit = (next) => onChange(next.join('\n'))
  const setRow = (i, text) => commit(items.map((r, j) => (j === i ? text : r)))
  const insertAfter = (i, texts = ['']) => {
    if (items.length + texts.length > max) texts = texts.slice(0, Math.max(0, max - items.length))
    if (!texts.length) return
    commit([...items.slice(0, i + 1), ...texts, ...items.slice(i + 1)])
    setFocusAt({ i: i + texts.length, end: true })
  }
  const remove = (i) => {
    if (items.length === 1) { commit(['']); setFocusAt({ i: 0 }); return }
    commit(items.filter((_, j) => j !== i))
  }
  const moveUp = (i) => {
    if (i === 0) return
    const next = [...items];[next[i - 1], next[i]] = [next[i], next[i - 1]]
    commit(next)
  }

  const onKeyDown = (i) => (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      const el = e.currentTarget
      const before = el.value.slice(0, el.selectionStart)
      const after = el.value.slice(el.selectionEnd)
      const next = [...items]
      next[i] = before
      if (items.length >= max) return
      next.splice(i + 1, 0, after)
      commit(next)
      setFocusAt({ i: i + 1 })
    } else if (e.key === 'Backspace' && e.currentTarget.value === '' && items.length > 1) {
      e.preventDefault()
      remove(i)
      setFocusAt({ i: Math.max(0, i - 1), end: true })
    } else if (e.key === 'ArrowUp' && e.currentTarget.selectionStart === 0 && i > 0) {
      e.preventDefault(); setFocusAt({ i: i - 1, end: true })
    } else if (e.key === 'ArrowDown' && e.currentTarget.selectionEnd === e.currentTarget.value.length && i < items.length - 1) {
      e.preventDefault(); setFocusAt({ i: i + 1 })
    }
  }

  const onPaste = (i) => (e) => {
    const text = e.clipboardData.getData('text')
    if (!/\r?\n/.test(text)) return
    e.preventDefault()
    const pasted = text.split(/\r?\n/).map((l) => strip(l).trim()).filter(Boolean)
    if (!pasted.length) return
    const el = e.currentTarget
    const before = el.value.slice(0, el.selectionStart)
    const after = el.value.slice(el.selectionEnd)
    const cut = (str) => str.slice(0, maxLength)
    const first = cut(before + pasted[0])
    const rest = pasted.slice(1)
    if (rest.length) rest[rest.length - 1] += after
    else return setRow(i, cut(before + pasted[0] + after))
    for (let k = 0; k < rest.length; k++) rest[k] = cut(rest[k])
    const next = [...items]
    next[i] = first
    const room = Math.max(0, max - items.length)
    next.splice(i + 1, 0, ...rest.slice(0, room))
    commit(next)
    setFocusAt({ i: i + Math.min(rest.length, room), end: true })
  }

  return (
    <div className="sm:col-span-2">
      <span className="text-[13px] font-medium text-muted">{label}</span>
      <div className="mt-1.5 space-y-2">
        {items.map((text, i) => (
          <div key={i} className="flex items-start gap-1.5">
            <span aria-hidden className="mt-[17px] ml-1 mr-1 h-1.5 w-1.5 rounded-full bg-ink shrink-0" />
            <AutoGrow
              inputRef={{ get current() { return refs.current[i] }, set current(el) { refs.current[i] = el } }}
              value={text}
              placeholder={i === 0 ? placeholder : ''}
              aria-label={`${label} ${i + 1}`}
              maxLength={maxLength}
              onChange={(e) => setRow(i, e.target.value.replace(/\r?\n/g, ' '))}
              onKeyDown={onKeyDown(i)}
              onPaste={onPaste(i)}
              className="flex-1 min-w-0 rounded-lg border border-line bg-surface px-3 py-2.5 text-sm leading-relaxed outline-none transition focus:border-accent focus:ring-4 focus:ring-[var(--glow)]"
            />
            <button type="button" onClick={() => moveUp(i)} disabled={i === 0} aria-label="Move up"
              className="mt-1 h-8 w-8 shrink-0 grid place-items-center rounded-md text-faint hover:bg-sunken hover:text-ink disabled:opacity-30 disabled:hover:bg-transparent">↑</button>
            <button type="button" onClick={() => remove(i)} aria-label="Remove"
              className="mt-1 h-8 w-8 shrink-0 grid place-items-center rounded-md text-faint hover:bg-sunken hover:text-bad">×</button>
          </div>
        ))}
      </div>
      {items.length < max && (
        <button type="button" onClick={() => insertAfter(items.length - 1)}
          className="mt-2 ml-4 inline-flex items-center gap-1.5 rounded-lg border border-dashed border-line px-3 py-2 text-[13px] font-medium hover:border-faint">
          + {addLabel}
        </button>
      )}
      <p className="mt-1.5 ml-4 text-xs text-muted">{hint || 'Press Enter for a new one. Pasting several lines splits them up.'}</p>
    </div>
  )
}

/** Skills as chips. Type to get suggestions from the built-in list; + or Enter adds one. */
export function SkillsInput({ label = 'Skills', value, onChange, hint, max = 100 }) {
  const skills = splitList(value)
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)
  const [hi, setHi] = useState(0)
  const boxRef = useRef(null)
  const inputRef = useRef(null)
  const sugg = suggestSkills(q, skills)
  const typed = q.trim()
  const typedNew = typed && !skills.some((s) => s.toLowerCase() === typed.toLowerCase())
    && !sugg.some((s) => s.name.toLowerCase() === typed.toLowerCase())
  const options = [...sugg.map((s) => ({ name: s.name, group: s.group })), ...(typedNew ? [{ name: typed, custom: true }] : [])]

  useEffect(() => { setHi(0) }, [q])
  useEffect(() => {
    const close = (e) => { if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false) }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [])

  const save = (list) => onChange(list.join('\n'))
  const add = (name) => {
    const n = name.trim().replace(/\s*·\s*/g, ' ')
    if (!n || skills.length >= max || skills.some((s) => s.toLowerCase() === n.toLowerCase())) return
    save([...skills, n]); setQ(''); inputRef.current?.focus()
  }
  const removeAt = (i) => save(skills.filter((_, j) => j !== i))

  const onKeyDown = (e) => {
    if (e.key === 'ArrowDown' && options.length) { e.preventDefault(); setOpen(true); setHi((h) => (h + 1) % options.length) }
    else if (e.key === 'ArrowUp' && options.length) { e.preventDefault(); setHi((h) => (h - 1 + options.length) % options.length) }
    else if (e.key === 'Enter') { e.preventDefault(); if (options[hi]) add(options[hi].name); else if (typed) add(typed) }
    else if (e.key === 'Escape') setOpen(false)
    else if (e.key === 'Backspace' && !q && skills.length) removeAt(skills.length - 1)
  }

  const showList = open && typed && options.length > 0

  return (
    <div className="sm:col-span-2" ref={boxRef}>
      <span className="text-[13px] font-medium text-muted">{label}</span>
      {skills.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-2">
          {skills.map((s, i) => (
            <span key={`${s}-${i}`} className="inline-flex items-center gap-1 rounded-full border border-line bg-sunken pl-3 pr-1 py-1 text-[13px]">
              {s}
              <button type="button" onClick={() => removeAt(i)} aria-label={`Remove ${s}`}
                className="h-5 w-5 grid place-items-center rounded-full text-faint hover:text-bad hover:bg-surface">×</button>
            </span>
          ))}
        </div>
      )}
      <div className="relative mt-2">
        <input
          ref={inputRef}
          value={q}
          onChange={(e) => { setQ(e.target.value); setOpen(true) }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          maxLength={200} disabled={skills.length >= max}
          placeholder={skills.length >= max ? `That's the limit of ${max} skills` : skills.length ? 'Add another skill' : 'Start typing a skill, like Java or Excel'}
          autoComplete="off" name="resqora-skill-search" role="combobox" aria-expanded={showList} aria-autocomplete="list"
          className="w-full h-10 rounded-lg border border-line bg-surface px-3 text-sm outline-none transition focus:border-accent focus:ring-4 focus:ring-[var(--glow)]"
        />
        {showList && (
          <ul role="listbox" className="absolute z-20 left-0 right-0 mt-1.5 max-h-72 overflow-auto rounded-xl border border-line bg-surface shadow-lg">
            {options.map((o, i) => (
              <li key={o.name} role="option" aria-selected={i === hi}
                onPointerDown={(e) => { e.preventDefault(); add(o.name) }}
                onMouseEnter={() => setHi(i)}
                className={`flex items-center justify-between gap-3 px-3 py-2.5 cursor-pointer text-sm border-t border-line first:border-t-0 ${i === hi ? 'bg-sunken' : ''}`}>
                <span className="min-w-0 truncate">
                  {o.custom ? <>Add “{o.name}” as typed</> : <Highlight text={o.name} q={typed} />}
                  {o.group && <span className="ml-2 text-xs text-faint">{o.group}</span>}
                </span>
                <span aria-hidden className={`h-7 w-7 shrink-0 grid place-items-center rounded-md text-base leading-none ${o.custom ? 'border border-line' : 'bg-ink text-paper'}`}>+</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <p className="mt-1.5 text-xs text-muted">{hint || 'Tap + to add a suggestion, or press Enter to add what you typed.'}</p>
    </div>
  )
}

function Highlight({ text, q }) {
  const i = text.toLowerCase().indexOf(q.toLowerCase())
  if (i < 0) return text
  return <>{text.slice(0, i)}<b className="font-semibold">{text.slice(i, i + q.length)}</b>{text.slice(i + q.length)}</>
}
