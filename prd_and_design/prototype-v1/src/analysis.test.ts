import assert from 'node:assert/strict'
import test from 'node:test'
import { anomalyContribution, completenessPercentage, conservedTimeParts, percentile } from './analysis.ts'

test('percentile excludes invalid durations and interpolates', () => {
  assert.equal(percentile([2, 4, 8, Number.NaN, -1], .5), 4)
  assert.equal(percentile([2, 4, 8], .95), 7.6)
  assert.equal(percentile([-1], .5), null)
})

test('anomaly contribution applies five-sample and dual-threshold rules', () => {
  const samples = [2, 2, 2, 2, 20, 2].map((model, index) => ({
    turnId: `turn-${index}`,
    model,
    tool: index === 0 ? 12 : 4
  }))
  const all = anomalyContribution(samples, samples.map(sample => sample.turnId))
  assert.equal(all[0].excess, 18)
  assert.equal(all[1].excess, 8)
  assert.equal(all[0].share, 69)
  const filtered = anomalyContribution(samples, ['turn-0'])
  assert.equal(filtered[0].count, 1)
  assert.equal(filtered[0].excess, 0)
})

test('time categories conserve the turn duration after rounding', () => {
  const parts = conservedTimeParts(33, 33 * .35, 33 * .45, 33 * .05)
  assert.equal(parts.reduce((sum, value) => sum + value, 0), 33)
  assert.deepEqual(conservedTimeParts(42, 15.4, 21, 1.6), [15.4, 21, 1.6, 4])
})

test('completeness uses four equal coverage components', () => {
  assert.equal(completenessPercentage([100, 100, 68, 100]), 92)
  assert.throws(() => completenessPercentage([100, 100, 100]))
})
