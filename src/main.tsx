import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import { bootstrapObr } from './application/obrBootstrap';
import './index.css';

void bootstrapObr();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
