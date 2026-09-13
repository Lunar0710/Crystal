import React from 'react'
import { Newspaper, Clock } from 'lucide-react'

const news = [
  {
    id: 1,
    title: 'Crystal Client 1.0 — Official Launch',
    excerpt: 'After months of development, Crystal Client 1.0 is officially released. Featuring a brand new modular HUD system, improved performance, Crystal Store integration, and full Fabric 1.21.4 support.',
    date: '2 hours ago',
    tag: 'Release',
    tagColor: 'bg-crystal-success/20 text-crystal-success border border-crystal-success/30',
  },
  {
    id: 2,
    title: 'New Cosmetics: Crystal Wings Collection',
    excerpt: 'Show off your style with the new Crystal Wings cape collection available in the Crystal Store. Featuring Aurora, Void and Galaxy variants.',
    date: '1 day ago',
    tag: 'Cosmetics',
    tagColor: 'bg-crystal-accent/20 text-crystal-accent border border-crystal-accent/30',
  },
  {
    id: 3,
    title: 'Performance Update: 30% Lower GPU Usage',
    excerpt: 'Our improved rendering pipeline reduces GPU usage by up to 30% on mid-range hardware. The update also addresses several memory leaks found in the HUD rendering system.',
    date: '3 days ago',
    tag: 'Update',
    tagColor: 'bg-crystal-warning/20 text-crystal-warning border border-crystal-warning/30',
  },
  {
    id: 4,
    title: 'Crystal Store: Coming Q1 2025',
    excerpt: 'The Crystal Store will launch in Q1 2025 featuring Crystal Coins, premium cosmetics, profile customization and more.',
    date: '1 week ago',
    tag: 'Announcement',
    tagColor: 'bg-crystal-accent-2/20 text-crystal-accent-2 border border-crystal-accent-2/30',
  },
]

export function News() {
  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center gap-2">
        <Newspaper size={20} className="text-crystal-accent" />
        <h1 className="text-xl font-bold text-crystal-text">News</h1>
      </div>

      <div className="space-y-3">
        {news.map(item => (
          <div key={item.id} className="crystal-card p-5 hover:border-crystal-accent/40 transition-colors cursor-pointer">
            <div className="flex items-center gap-2 mb-2">
              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${item.tagColor}`}>{item.tag}</span>
              <span className="text-crystal-muted text-xs flex items-center gap-1">
                <Clock size={10} /> {item.date}
              </span>
            </div>
            <h3 className="text-crystal-text font-semibold mb-2">{item.title}</h3>
            <p className="text-crystal-muted text-sm leading-relaxed">{item.excerpt}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
