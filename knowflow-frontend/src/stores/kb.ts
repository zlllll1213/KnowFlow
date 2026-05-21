import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getKbList, getKbDetail } from '@/api/kb'
import type { KbVO } from '@/types/kb'

export const useKbStore = defineStore('kb', () => {
  const knowledgeBaseList = ref<KbVO[]>([])
  const currentKb = ref<KbVO | null>(null)

  async function loadList() {
    knowledgeBaseList.value = await getKbList()
  }

  async function loadDetail(id: number) {
    currentKb.value = await getKbDetail(id)
  }

  function setCurrentKb(kb: KbVO | null) {
    currentKb.value = kb
  }

  return { knowledgeBaseList, currentKb, loadList, loadDetail, setCurrentKb }
})
