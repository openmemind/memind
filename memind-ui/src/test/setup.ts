import "@testing-library/jest-dom/vitest"

Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }),
})

Object.defineProperty(window, "scrollTo", {
  writable: true,
  value: vi.fn(),
})

class ResizeObserverMock {
  private callback: ResizeObserverCallback

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
  }

  observe = vi.fn((target: Element) => {
    const width =
      target instanceof HTMLElement && target.offsetWidth > 0
        ? target.offsetWidth
        : 1220
    const height =
      target instanceof HTMLElement && target.offsetHeight > 0
        ? target.offsetHeight
        : 760
    const entry = {
      target,
      contentRect: {
        bottom: height,
        height,
        left: 0,
        right: width,
        top: 0,
        width,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      },
      borderBoxSize: [
        {
          blockSize: height,
          inlineSize: width,
        },
      ],
      contentBoxSize: [
        {
          blockSize: height,
          inlineSize: width,
        },
      ],
      devicePixelContentBoxSize: [
        {
          blockSize: height,
          inlineSize: width,
        },
      ],
    } as ResizeObserverEntry

    queueMicrotask(() => {
      this.callback([entry], this as unknown as ResizeObserver)
    })
  })

  unobserve = vi.fn()
  disconnect = vi.fn()
}

Object.defineProperty(window, "ResizeObserver", {
  writable: true,
  value: ResizeObserverMock,
})

Object.defineProperty(globalThis, "ResizeObserver", {
  writable: true,
  value: ResizeObserverMock,
})

Object.defineProperty(HTMLElement.prototype, "offsetWidth", {
  configurable: true,
  get() {
    const width = Number.parseFloat(window.getComputedStyle(this).width)
    return Number.isFinite(width) && width > 0 ? width : 1220
  },
})

Object.defineProperty(HTMLElement.prototype, "offsetHeight", {
  configurable: true,
  get() {
    const height = Number.parseFloat(window.getComputedStyle(this).height)
    return Number.isFinite(height) && height > 0 ? height : 760
  },
})

class DOMMatrixReadOnlyMock {
  m22 = 1
}

Object.defineProperty(window, "DOMMatrixReadOnly", {
  writable: true,
  value: DOMMatrixReadOnlyMock,
})

Object.defineProperty(HTMLCanvasElement.prototype, "getContext", {
  writable: true,
  value: vi.fn(() => ({
    arc: vi.fn(),
    beginPath: vi.fn(),
    bezierCurveTo: vi.fn(),
    clearRect: vi.fn(),
    closePath: vi.fn(),
    fill: vi.fn(),
    fillRect: vi.fn(),
    fillText: vi.fn(),
    lineTo: vi.fn(),
    measureText: vi.fn((text: string) => ({ width: text.length * 6 })),
    moveTo: vi.fn(),
    quadraticCurveTo: vi.fn(),
    restore: vi.fn(),
    save: vi.fn(),
    scale: vi.fn(),
    setTransform: vi.fn(),
    stroke: vi.fn(),
  })),
})
