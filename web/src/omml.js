import { DOMParser, XMLSerializer } from '@xmldom/xmldom';

const MATH = 'http://schemas.openxmlformats.org/officeDocument/2006/math';
const XML = 'http://www.w3.org/XML/1998/namespace';
const expressionKinds = new Set(['r', 'sSup', 'sSub', 'sSubSup', 'f', 'rad', 'd', 'm', 'func', 'acc', 'bar']);
const delimiterPairs = new Map([['(', ')'], ['[', ']'], ['{', '}'], ['|', '|'], ['‖', '‖']]);

function parseXml(source) {
  if (/<!DOCTYPE|<!ENTITY/i.test(source)) throw new Error('External XML declarations are not supported.');
  return new DOMParser({ onError: (_level, message) => { throw new Error(message); } }).parseFromString(source, 'application/xml');
}

function elements(parent) { return Array.from(parent.childNodes).filter(node => node.nodeType === 1); }
function mathChild(parent, name) { return elements(parent).find(node => node.namespaceURI === MATH && node.localName === name); }
function element(document, name) { return document.createElementNS(MATH, 'm:' + name); }

function preserveRawText(parent, document) {
  for (const child of Array.from(parent.childNodes)) {
    if (child.nodeType === 1) preserveRawText(child, document);
    else if ((child.nodeType === 3 || child.nodeType === 4) && parent.localName !== 't') {
      if (!child.data.trim()) { parent.removeChild(child); continue; }
      // Upstream can leave a relation directly in m:e after a n-ary operand.
      // Word silently drops such text; all visible text must live in m:r/m:t.
      const run = element(document, 'r');
      const text = element(document, 't');
      text.setAttributeNS(XML, 'xml:space', 'preserve');
      text.appendChild(document.createTextNode(child.data));
      run.appendChild(text);
      parent.replaceChild(run, child);
    }
  }
}

function runText(node) {
  if (node?.namespaceURI !== MATH || node.localName !== 'r') return null;
  const children = elements(node);
  if (children.some(child => child.localName !== 't' && child.localName !== 'rPr')) return null;
  return children.filter(child => child.localName === 't').map(child => child.textContent).join('');
}

function normalizeStructure(parent, document) {
  for (const child of elements(parent)) normalizeStructure(child, document);
  let children = elements(parent);
  for (let index = 0; index < children.length; index++) {
    const node = children[index];
    if (node.namespaceURI !== MATH || node.localName !== 'nary') continue;
    const operand = mathChild(node, 'e');
    const next = children[index + 1];
    if (operand && !elements(operand).length && next && expressionKinds.has(next.localName)) {
      const text = runText(next);
      // Attach only the immediately following expression, never a relation.
      if (text === null || !/[=<>≤≥≈≠∈]/u.test(text)) operand.appendChild(next);
    }
  }
  children = elements(parent);
  for (let index = 0; index + 2 < children.length; index++) {
    const left = children[index];
    const matrix = children[index + 1];
    const right = children[index + 2];
    const opening = runText(left);
    const closing = runText(right);
    if (matrix.namespaceURI !== MATH || matrix.localName !== 'm' || !delimiterPairs.has(opening) || delimiterPairs.get(opening) !== closing) continue;
    const delimiter = element(document, 'd');
    const properties = element(document, 'dPr');
    for (const [name, value] of [['begChr', opening], ['endChr', closing], ['grow', '1']]) {
      const property = element(document, name);
      property.setAttributeNS(MATH, 'm:val', value);
      properties.appendChild(property);
    }
    const content = element(document, 'e');
    parent.insertBefore(delimiter, left);
    delimiter.appendChild(properties);
    delimiter.appendChild(content);
    content.appendChild(matrix);
    parent.removeChild(left);
    parent.removeChild(right);
    children = elements(parent);
  }
}

function signature(parts) {
  return Array.from(parts.join('').replace(/[\s\u200b\u2061\u2062\u2063\u2064]/gu, '')).sort().join('');
}

export function mathmlSymbolSignature(source) {
  const document = parseXml(source);
  const parts = [];
  for (const node of Array.from(document.getElementsByTagName('*'))) {
    if (['mi', 'mn', 'mo', 'mtext', 'ms'].includes(node.localName)) parts.push(node.textContent);
  }
  return signature(parts);
}

export function ommlSymbolSignature(source) {
  const document = parseXml(source);
  const parts = [];
  for (const node of Array.from(document.getElementsByTagName('*'))) {
    if (node.namespaceURI !== MATH) continue;
    if (node.localName === 't') parts.push(node.textContent);
    if (['chr', 'begChr', 'endChr'].includes(node.localName)) parts.push(node.getAttributeNS(MATH, 'val'));
    for (const child of Array.from(node.childNodes)) {
      if ((child.nodeType === 3 || child.nodeType === 4) && child.data.trim() && node.localName !== 't') throw new Error('Word math contains text outside a native math run.');
    }
  }
  return signature(parts);
}

export function normalizeOmml(source, mathml) {
  const document = parseXml(source);
  preserveRawText(document.documentElement, document);
  normalizeStructure(document.documentElement, document);
  const normalized = new XMLSerializer().serializeToString(document);
  // Reordering roots/scripts is legitimate, so compare a multiset of symbols.
  // A conversion that drops or invents a symbol falls back to exact editable TeX.
  if (mathmlSymbolSignature(mathml) !== ommlSymbolSignature(normalized)) throw new Error('Word equation conversion could not preserve every symbol.');
  return normalized;
}
