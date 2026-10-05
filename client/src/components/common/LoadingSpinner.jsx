import React from 'react';
import { Loader2 } from 'lucide-react';

export default function LoadingSpinner({ text = 'Loading...' }) {
  return (
    <div className="loading-container">
      <Loader2 className="spinner-icon" size={28} />
      {text && <p className="loading-text">{text}</p>}
    </div>
  );
}
