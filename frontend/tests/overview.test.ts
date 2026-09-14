import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'
import OverviewPage from '../src/components/OverviewPage.vue'
import AnomalyContributionPanel from '../src/components/AnomalyContributionPanel.vue'
import { analyzerDataKey } from '../src/data/analyzerData'
import { createMockAnalyzerData } from '../src/data/mockAdapter'

describe('总览数据适配', () => {
  it('按独立搜索项筛选并传出实际点击轮次，运行中轮次不参与异常统计', async () => {
    const data = createMockAnalyzerData()
    const wrapper = mount(OverviewPage, {
      global: { plugins: [ElementPlus], provide: { [analyzerDataKey as symbol]: data }, stubs: { TrendChart: true } },
    })
    try {
      expect(wrapper.findAll('button.session-row')).toHaveLength(8)
      expect(wrapper.getComponent(AnomalyContributionPanel).props('turnIds')).not.toContain('turn-demo-008')
      await wrapper.get('input[placeholder="会话 ID"]').setValue('SESSION-DEMO-B37C')
      expect(wrapper.findAll('button.session-row')).toHaveLength(1)
      await wrapper.get('button.session-row').trigger('click')
      expect(wrapper.emitted('open')?.[0]?.[0]).toEqual(data.sessions[1])
      await wrapper.get('input[placeholder="用户问题关键词"]').setValue('不匹配的虚构问题')
      expect(wrapper.findAll('button.session-row')).toHaveLength(0)
      expect(wrapper.text()).toContain('没有匹配的会话')
      expect(wrapper.getComponent(AnomalyContributionPanel).props('turnIds')).toEqual([])
    } finally {
      wrapper.unmount()
    }
  })

  it('不同适配器实例之间不共享可变演示数据', () => {
    const first = createMockAnalyzerData()
    first.sessions[0].title = '独立测试数据'
    first.timelineItems[0].raw.type = 'test'
    const second = createMockAnalyzerData()
    expect(second.sessions[0].title).not.toBe('独立测试数据')
    expect(second.timelineItems[0].raw.type).toBe('user_message')
  })
})
