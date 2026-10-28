import katex from 'katex';
import { mathOptions, convertBatch } from './math.js';

function renderInline(parent, tokens) {
  for (const token of tokens) {
    let element = document.createElement('span');
    if (token.math) {
      try { katex.render(token.text, element, mathOptions); }
      catch { element.className = 'math-error'; element.textContent = `Equation needs review: \\(${token.text}\\)`; }
    } else {
      element.textContent = token.text;
      if (token.bold) { const wrapper = document.createElement('strong'); wrapper.append(element); element = wrapper; }
      if (token.italic) { const wrapper = document.createElement('em'); wrapper.append(element); element = wrapper; }
    }
    parent.append(element);
  }
}

window.renderNote = (blocks) => {
  const root = document.getElementById('document');
  root.replaceChildren();
  let list = null;
  let listType = null;
  for (const block of blocks) {
    if (block.type !== 'list') { list = null; listType = null; }
    let element;
    if (block.type === 'equation') {
      element = document.createElement('div'); element.className = 'equation';
      try { katex.render(block.latex, element, { ...mathOptions, displayMode: true }); }
      catch { element.className = 'math-error'; element.textContent = `Equation needs review:\n\\[${block.latex}\\]`; }
    } else if (block.type === 'table') {
      element = document.createElement('table');
      block.rows.forEach((row, index) => {
        const tr = document.createElement('tr');
        row.forEach(tokens => { const cell = document.createElement(index === 0 ? 'th' : 'td'); renderInline(cell, tokens); tr.append(cell); });
        element.append(tr);
      });
    } else if (block.type === 'list') {
      const type = block.ordered ? 'ol' : 'ul';
      if (!list || listType !== type) { list = document.createElement(type); listType = type; if (block.ordered) list.start = block.number; root.append(list); }
      element = document.createElement('li'); renderInline(element, block.tokens); list.append(element); continue;
    } else {
      element = document.createElement(block.type === 'heading' ? `h${block.level}` : 'p');
      renderInline(element, block.tokens);
    }
    root.append(element);
  }
  if (!blocks.length) { const empty = document.createElement('p'); empty.className = 'empty'; empty.textContent = 'Add a photo or start typing to create your document.'; root.append(empty); }
};

window.convertBatch = convertBatch;
window.documentReady = true;
