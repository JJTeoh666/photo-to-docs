import test from 'node:test';
import assert from 'node:assert/strict';
import { equationToOmml, convertBatch } from '../src/math.js';
import { normalizeOmml, mathmlSymbolSignature, ommlSymbolSignature } from '../src/omml.js';
import { DOMParser } from '@xmldom/xmldom';
import katex from 'katex';

const fixtures = [
  ['x^2 + y_1 = \\alpha', '<m:sSup>'],
  ['\\frac{1}{x+1}', '<m:f>'],
  ['\\sqrt{x} + \\sqrt[3]{y}', '<m:rad>'],
  ['\\sum_{i=1}^{n} i', '<m:nary>'],
  ['\\int_0^1 x^2\\,dx', '<m:nary>'],
  ['\\begin{pmatrix}1 & 2 \\\\ 3 & 4\\end{pmatrix}', '<m:m>'],
  ['\\begin{aligned}x+y &= 3 \\\\ x-y &= 1\\end{aligned}', '<m:m>'],
  ['|x|', '<m:oMath'],
];
for (const [source, expected] of fixtures) {
  test(`editable Word structure for ${source}`, () => {
    const omml = equationToOmml(source);
    assert.ok(omml.includes('<m:oMath'));
    assert.ok(omml.includes(expected), omml);
    assert.ok(!omml.includes('<img'));
  });
}
test('invalid equations remain absent from conversion map, including mixed batches', () => {
  assert.throws(() => equationToOmml('\\frac{1}'));
  assert.throws(() => equationToOmml('\\unknowncommand{x}'));
  const converted = convertBatch(['x^2', '\\frac{1}', '<script>alert(1)</script>']);
  assert.ok(converted['x^2']);
  assert.equal(converted['\\frac{1}'], undefined);
  assert.equal(Object.values(converted).some(value => value.includes('<script>')), false);
});
test('oversized inputs are rejected', () => assert.throws(() => equationToOmml('x'.repeat(20_001))));
test('unsupported MathML structures cannot silently disappear from a Word equation', () => {
  assert.throws(() => equationToOmml('\\phantom{x}+y'));
  assert.equal(convertBatch(['\\phantom{x}+y'])['\\phantom{x}+y'], undefined);
});

test('integral and sum relations survive as visible Word text with matching source symbols', () => {
  const source = '\\int_0^1 x^2\\,dx = \\frac{1}{3}, \\qquad \\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}';
  const omml = equationToOmml(source);
  const mathml = katex.renderToString(source, { output: 'mathml', displayMode: true });
  assert.equal(ommlSymbolSignature(omml), mathmlSymbolSignature(mathml));
  const document = new DOMParser().parseFromString(omml, 'application/xml');
  const text = Array.from(document.getElementsByTagName('m:t')).map(node => node.textContent).join('');
  assert.equal((text.match(/=/g) || []).length, 3, text);
  assert.ok(text.includes('i='), text);
  const naries = Array.from(document.getElementsByTagName('m:nary'));
  assert.ok(naries.every(node => Array.from(node.childNodes).filter(child => child.localName === 'e').every(child => child.childNodes.length > 0)));
});

test('matrix parentheses are growing native Word delimiters', () => {
  const omml = equationToOmml('\\begin{pmatrix}1 & 2 \\\\ 3 & 4\\end{pmatrix}');
  assert.ok(omml.includes('<m:d>'), omml);
  assert.ok(omml.includes('<m:begChr m:val="("'), omml);
  assert.ok(omml.includes('<m:endChr m:val=")"'), omml);
  assert.ok(omml.includes('<m:grow m:val="1"'), omml);
});

test('bare relations are wrapped and no symbol can silently disappear', () => {
  const mathml = '<math xmlns="http://www.w3.org/1998/Math/MathML"><mi>i</mi><mo>=</mo><mn>2</mn></math>';
  const raw = '<m:oMath xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"><m:r><m:t>i</m:t></m:r>=<m:r><m:t>2</m:t></m:r></m:oMath>';
  assert.throws(() => ommlSymbolSignature(raw));
  const fixed = normalizeOmml(raw, mathml);
  assert.ok(fixed.includes('<m:t xml:space="preserve">=</m:t>'), fixed);
  assert.equal(ommlSymbolSignature(fixed), mathmlSymbolSignature(mathml));
  assert.throws(() => normalizeOmml(raw.replace('>=<', '><'), mathml));
});
