function App() {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-slate-700 bg-slate-900/50 backdrop-blur-sm sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center">
              <span className="text-xl font-bold text-white">
                Seat<span className="text-indigo-400">Lock</span>
              </span>
            </div>
            <div className="flex items-center gap-4">
              <span className="px-2 py-1 text-xs font-mono text-slate-500 bg-slate-800 rounded">
                v0.1.0
              </span>
            </div>
          </div>
        </div>
      </header>
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-white mb-4">SeatLock Frontend</h1>
          <p className="text-slate-400">Project scaffold complete. Ready for Block 2 implementation.</p>
        </div>
      </main>
    </div>
  )
}

export default App