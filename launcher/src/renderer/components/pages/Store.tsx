import React from 'react'
import { ShoppingBag, Star, Sparkles } from 'lucide-react'

export function Store() {
  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center gap-2">
        <ShoppingBag size={20} className="text-crystal-accent" />
        <h1 className="text-xl font-bold text-crystal-text">Crystal Store</h1>
      </div>

      <div className="crystal-card p-8 text-center">
        <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-crystal-gradient flex items-center justify-center shadow-glow">
          <ShoppingBag size={28} className="text-white" />
        </div>
        <h2 className="text-crystal-text font-bold text-lg mb-2">Coming Soon</h2>
        <p className="text-crystal-muted text-sm max-w-xs mx-auto">
          The Crystal Store is under development. Premium cosmetics, Crystal Coins and exclusive items will be available soon.
        </p>

        <div className="mt-6 grid grid-cols-3 gap-3 max-w-sm mx-auto">
          {['Crystal Wings', 'Glow Effects', 'Profile Banners'].map(item => (
            <div key={item} className="crystal-card p-3 text-center opacity-50">
              <Sparkles size={20} className="mx-auto mb-1 text-crystal-accent" />
              <p className="text-crystal-text text-xs font-medium">{item}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
