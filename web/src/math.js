import katex from 'katex';
import { mml2omml } from 'mathml2omml';
import { normalizeOmml } from './omml.js';

export const mathOptions = { trust: false, strict: 'error', throwOnError: true, maxExpand: 500, maxSize: 20 };

export function equationToOmml(latex) {
  if (typeof latex !== 'string' || latex.length > 20_000) throw new Error('Equation exceeds the supported size.');
  const markup = katex.renderToString(latex, { ...mathOptions, output: 'mathml', displayMode: true });
  // KaTeX wraps MathML in span.katex; take the actual XML, retaining namespace.
  const start = markup.indexOf('<math');
  const end = markup.lastIndexOf('</math>');
  if (start < 0 || end < 0) throw new Error('No MathML was produced.');
  const mathml = markup.slice(start, end + 7).replace(/<annotation\b[^>]*>[\s\S]*?<\/annotation>/g, '');
  const supported = new Set(['math', 'semantics', 'menclose', 'mfrac', 'mglyph', 'mmultiscripts', 'mrow', 'mspace', 'msqrt', 'mstyle', 'msub', 'msubsup', 'msup', 'mtable', 'mtd', 'mtr', 'munderover', 'mtext', 'mi', 'mn', 'ms', 'mo', 'munder', 'mover', 'mroot']);
  for (const match of mathml.matchAll(/<\/?([a-zA-Z][\w:-]*)\b/g)) {
    if (!supported.has(match[1])) throw new Error(`Unsupported Word math structure: ${match[1]}`);
  }
  // Keep XML entities escaped: the converter's default decoding can emit raw < or &.
  const omml = mml2omml(mathml, { disableDecode: true });
  if (!omml.includes('<m:oMath') || !omml.includes('</m:oMath>')) throw new Error('No Word equation was produced.');
  return normalizeOmml(omml, mathml);
}

export function convertBatch(equations) {
  const converted = {};
  for (const equation of equations) {
    try { converted[equation] = equationToOmml(equation); }
    catch { /* The native export retains exact unsupported TeX as visible text. */ }
  }
  return converted;
}
