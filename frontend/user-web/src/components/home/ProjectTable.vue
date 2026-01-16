<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import type { ProjectVO, FolderVO } from '@/types/api'
import NeonTag from '@/components/base/NeonTag.vue'
import PillButton from '@/components/base/PillButton.vue'

const router = useRouter()
const projectStore = useProjectStore()

const emit = defineEmits<{
  editFolder: [folder: FolderVO]
  deleteFolder: [folder: FolderVO]
  deleteProject: [project: ProjectVO]
  moveProject: [project: ProjectVO]
}>()

// Tab 切换: 'projects' | 'folders'
const activeTab = ref<'projects' | 'folders'>('projects')

// 当前选中的文件夹（用于筛选项目）
const selectedFolderId = ref<number | null>(null)
const selectedFolderName = ref<string>('')

// 分页状态
const currentPage = ref(1)
const pageSize = 3

// 切换 Tab 时重置分页
watch(activeTab, () => {
  currentPage.value = 1
  // 切换到文件夹Tab时清除筛选
  if (activeTab.value === 'folders') {
    selectedFolderId.value = null
    selectedFolderName.value = ''
  }
})

// 获取当前列表数据
const currentList = computed(() => {
  if (activeTab.value === 'projects') {
    // 如果选中了文件夹，筛选该文件夹下的项目
    if (selectedFolderId.value !== null) {
      return projectStore.projects.filter(p => p.folderId === selectedFolderId.value)
    }
    return projectStore.projects
  }
  return projectStore.folders
})

// 点击文件夹进入
const handleFolderClick = (folder: FolderVO) => {
  selectedFolderId.value = folder.id
  selectedFolderName.value = folder.name
  activeTab.value = 'projects'
  currentPage.value = 1
}

// 返回全部项目
const clearFolderFilter = () => {
  selectedFolderId.value = null
  selectedFolderName.value = ''
  currentPage.value = 1
}

// 分页后的数据
const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  const end = start + pageSize
  return currentList.value.slice(start, end)
})

// 总页数
const totalPages = computed(() => {
  return Math.ceil(currentList.value.length / pageSize) || 1
})

const formatDate = (date: string) => {
  return new Date(date).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

// 实时计算文件夹的项目数量
const getFolderProjectCount = (folderId: number) => {
  return projectStore.projects.filter(p => p.folderId === folderId).length
}

const handleEditProject = (project: ProjectVO) => {
  router.push(`/editor/${project.id}`)
}

// Helper to get folder name
const getFolderName = (folderId: number | null) => {
  if (!folderId) return '未分类'
  const folder = projectStore.getFolderById(folderId)
  return folder?.name || '未知文件夹'
}

// 分页操作
const goToPage = (page: number) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
  }
}
</script>

<template>
  <div class="card overflow-hidden flex flex-col h-full">
    <!-- Tab 切换 -->
    <div class="flex items-center gap-1 px-4 py-2 border-b border-border-default">
      <button
        :class="[
          'px-4 py-1.5 rounded text-sm font-medium transition-colors',
          activeTab === 'projects'
            ? 'bg-[#8B5CF6] text-white'
            : 'text-text-secondary hover:bg-bg-hover'
        ]"
        @click="activeTab = 'projects'; clearFolderFilter()"
      >
        项目 ({{ projectStore.projects.length }})
      </button>
      <button
        :class="[
          'px-4 py-1.5 rounded text-sm font-medium transition-colors',
          activeTab === 'folders'
            ? 'bg-[#8B5CF6] text-white'
            : 'text-text-secondary hover:bg-bg-hover'
        ]"
        @click="activeTab = 'folders'"
      >
        文件夹 ({{ projectStore.folders.length }})
      </button>
      
      <!-- 文件夹筛选提示 -->
      <div v-if="selectedFolderId !== null" class="flex items-center gap-2 ml-4 text-sm">
        <span class="text-text-tertiary">当前文件夹:</span>
        <span class="text-[#8B5CF6] font-medium">{{ selectedFolderName }}</span>
        <button 
          class="text-text-tertiary hover:text-text-primary transition-colors"
          @click="clearFolderFilter"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Table header -->
    <div class="grid grid-cols-12 gap-4 px-4 py-2 bg-bg-subtle border-b border-border-default text-xs font-medium text-text-secondary">
      <div class="col-span-6">名称</div>
      <div class="col-span-3">更新时间</div>
      <div class="col-span-3 text-right">操作</div>
    </div>

    <!-- Empty state -->
    <div v-if="currentList.length === 0" class="py-16 text-center">
      <div class="mb-4">
        <svg class="w-16 h-16 mx-auto text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 13h6m-3-3v6m5 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      </div>
      <p class="text-text-tertiary text-sm">暂无{{ activeTab === 'projects' ? '项目' : '文件夹' }}</p>
    </div>

    <!-- 项目列表 -->
    <div v-else-if="activeTab === 'projects'" class="divide-y divide-border-subtle flex-1 overflow-y-auto">
      <div
        v-for="project in paginatedList as ProjectVO[]"
        :key="project.id"
        class="grid grid-cols-12 gap-4 px-4 py-2 hover:bg-bg-hover transition-all"
      >
        <div class="col-span-6 flex items-center gap-2">
          <div class="w-8 h-8 rounded bg-bg-subtle overflow-hidden flex-shrink-0">
            <img
              v-if="project.coverUrl"
              :src="project.coverUrl"
              :alt="project.title || project.name"
              class="w-full h-full object-cover"
            >
            <div v-else class="w-full h-full flex items-center justify-center text-gray-400">
              <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
              </svg>
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-text-primary text-sm font-medium truncate">{{ project.title || project.name }}</p>
            <p v-if="project.description" class="text-text-tertiary text-xs truncate">{{ project.description }}</p>
          </div>
        </div>
        <div class="col-span-3 flex items-center">
          <span class="text-text-secondary text-sm">{{ formatDate(project.updatedAt) }}</span>
        </div>
        <div class="col-span-3 flex items-center justify-end gap-2">
          <button class="btn btn-secondary text-xs" @click="handleEditProject(project)">编辑</button>
          <button class="btn btn-secondary text-xs" @click="$emit('moveProject', project)">移动</button>
          <button class="p-2 rounded text-text-tertiary hover:bg-bg-hover hover:text-error transition-all" @click="$emit('deleteProject', project)">
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 文件夹列表 -->
    <div v-else class="divide-y divide-border-subtle flex-1 overflow-y-auto">
      <div
        v-for="folder in paginatedList as FolderVO[]"
        :key="folder.id"
        class="grid grid-cols-12 gap-4 px-4 py-2 hover:bg-bg-hover transition-all cursor-pointer"
        @click="handleFolderClick(folder)"
      >
        <div class="col-span-6 flex items-center gap-2">
          <div class="w-8 h-8 rounded bg-[#8B5CF6]/20 flex items-center justify-center flex-shrink-0">
            <svg class="w-4 h-4 text-[#8B5CF6]" fill="currentColor" viewBox="0 0 24 24">
              <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
            </svg>
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-text-primary text-sm font-medium truncate">{{ folder.name }}</p>
            <p class="text-text-tertiary text-xs">{{ getFolderProjectCount(folder.id) }} 个项目</p>
          </div>
        </div>
        <div class="col-span-3 flex items-center">
          <span class="text-text-secondary text-sm">{{ formatDate(folder.createdAt) }}</span>
        </div>
        <div class="col-span-3 flex items-center justify-end gap-2">
          <button class="btn btn-secondary text-xs" @click.stop="$emit('editFolder', folder)">编辑</button>
          <button class="p-2 rounded text-text-tertiary hover:bg-bg-hover hover:text-error transition-all" @click.stop="$emit('deleteFolder', folder)">
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="currentList.length > pageSize" class="px-4 py-2 border-t border-border-default flex items-center justify-between">
      <div class="text-sm text-text-tertiary">
        显示 {{ (currentPage - 1) * pageSize + 1 }}-{{ Math.min(currentPage * pageSize, currentList.length) }} / 共 {{ currentList.length }} 个{{ activeTab === 'projects' ? '项目' : '文件夹' }}
      </div>
      <div class="flex items-center gap-2">
        <button
          :disabled="currentPage === 1"
          class="px-3 py-1.5 rounded text-sm text-text-secondary hover:bg-bg-hover disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          @click="goToPage(currentPage - 1)"
        >
          上一页
        </button>
        <div class="flex items-center gap-1">
          <button
            v-for="page in totalPages"
            :key="page"
            :class="[
              'px-3 py-1.5 rounded text-sm transition-colors',
              page === currentPage
                ? 'bg-bg-subtle text-text-primary border border-border-default'
                : 'text-text-secondary hover:bg-bg-hover'
            ]"
            @click="goToPage(page)"
          >
            {{ page }}
          </button>
        </div>
        <button
          :disabled="currentPage === totalPages"
          class="px-3 py-1.5 rounded text-sm text-text-secondary hover:bg-bg-hover disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          @click="goToPage(currentPage + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>
