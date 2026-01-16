<script setup lang="ts">
import { ref, computed } from 'vue'
import type { StoryboardShotVO } from '@/types/api'
import { useEditorStore } from '@/stores/editor'
import { usePanelManagerStore } from '@/stores/panelManager'
import { shotApi, characterApi, sceneApi, propApi } from '@/api/apis'
import api from '@/api/index'
import AssetCell from './AssetCell.vue'
import AssetGenerateModal from './AssetGenerateModal.vue'
import AssetVersionModal from './AssetVersionModal.vue'

interface Props {
  shot: StoryboardShotVO
  selected: boolean
  isFirst: boolean  // 是否是第一条（禁用向上合并）
  isLast: boolean   // 是否是最后一条（禁用向下合并）
  onToggleSelect: () => void
  onUpdateScript: (scriptText: string) => void
  onMergeUp: () => void
  onMergeDown: () => void
  onDelete: () => void
}

const props = defineProps<Props>()

const editorStore = useEditorStore()
const panelManagerStore = usePanelManagerStore()

// 已删除/隐藏的人物名称
const dismissedCharacters = ref<Set<string>>(new Set())

// Editable script state
const isEditing = ref(false)
const editingText = ref('')
const maxLength = 4000

// Modal state
const showGenerateModal = ref(false)
const showVersionModal = ref(false)
const showCharacterSelectModal = ref(false)  // 角色选择弹窗
const showSceneSelectModal = ref(false)  // 场景选择弹窗
const showPropSelectModal = ref(false)  // 道具选择弹窗
const generateModalType = ref<'shot_image' | 'video'>('shot_image')
const versionModalAssetId = ref<number | null>(null)
const assetVersions = ref<any[]>([])

const startEditing = () => {
  editingText.value = props.shot.scriptText
  isEditing.value = true
}

const saveScript = () => {
  if (editingText.value.trim() !== props.shot.scriptText) {
    props.onUpdateScript(editingText.value.trim())
  }
  isEditing.value = false
}

const cancelEditing = () => {
  isEditing.value = false
  editingText.value = ''
}

// Display truncated script
const displayScript = computed(() => {
  return props.shot.scriptText
})

// 模糊人物名称过滤列表
const vagueCharacterNames = [
  '众舍友', '众人', '路人', '众', '路人甲', '路人乙', '其他人', '旁人', '众人们',
  '学生们', '同学们', '舍友甲', '舍友乙', '舍友丙', '舍友丁',
  '路人丙', '路人丁', '降人甲', '降人乙', '士兵甲', '士兵乙',
  '女子甲', '女子乙', '男子甲', '男子乙', '众人甲', '众人乙'
]

// 检查是否是模糊名称（包含“甲乙丙丁”或“众”“路人”等关键词）
const isVagueName = (name: string): boolean => {
  // 直接匹配模糊名称列表
  if (vagueCharacterNames.some(vague => name.includes(vague) || vague.includes(name))) return true
  // 包含“甲乙丙丁”结尾的名称
  if (/[甲乙丙丁]$/.test(name)) return true
  // 包含“众”字的名称
  if (name.includes('众')) return true
  // 包含“路人”的名称
  if (name.includes('路人')) return true
  return false
}

// 从剧本中提取人物名称
const extractedCharacters = computed(() => {
  const scriptText = props.shot.scriptText
  // 匹配 "出场人物:百里通、舍友甲" 格式
  const match = scriptText.match(/出场人物[::：]([^\n\(\uff08]+)/)
  if (match) {
    // 分割人物名称
    const names = match[1].split(/[,、，]/).map(n => n.trim()).filter(n => {
      // 过滤空名称和模糊名称
      if (!n) return false
      // 检查是否是模糊名称
      if (isVagueName(n)) return false
      return true
    })
    return names
  }
  return []
})

// 获取已经绑定的角色名称列表
const boundCharacterNames = computed(() => {
  return props.shot.characters.map(c => c.characterName)
})

// 获取未绑定的角色名称（排除已绑定的和已删除的）
const unboundCharacters = computed(() => {
  return extractedCharacters.value.filter(name => {
    // 排除已绑定的
    const isBound = boundCharacterNames.value.some(boundName => 
      boundName && (boundName.includes(name) || name.includes(boundName))
    )
    // 排除已删除的
    const isDismissed = dismissedCharacters.value.has(name)
    return !isBound && !isDismissed
  })
})

// 根据名称查找对应的项目角色（用于获取缩略图）
// 注意：后端返回的字段是 displayName，但前端类型定义用的是 name
const getCharacterByName = (name: string) => {
  return editorStore.characters.find(c => {
    // 尝试匹配 name 或 displayName
    const charName = (c as any).displayName || c.name
    return charName && (charName === name || charName.includes(name) || name.includes(charName))
  })
}

// 删除/隐藏提取的人物
const handleDismissCharacter = (charName: string) => {
  dismissedCharacters.value.add(charName)
}

// 点击人物图标，打开角色编辑面板
const handleGenerateCharacterFromScript = (characterName: string) => {
  // 查找是否已有对应的项目角色
  const existingCharacter = getCharacterByName(characterName)
  
  if (existingCharacter) {
    // 如果已有角色，打开编辑面板并显示现有信息
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: existingCharacter.id,
      characterName: characterName,
      existingThumbnailUrl: existingCharacter.thumbnailUrl,
      existingDescription: (existingCharacter as any).finalDescription || (existingCharacter as any).description
    })
  } else {
    // 如果是新角色，传入剧本文本让AI解析
    let scriptText = editorStore.originalScript || props.shot.scriptText
    
    // 限制文本长度，防止AI超时（最多2000字符）
    if (scriptText.length > 2000) {
      scriptText = scriptText.substring(0, 2000) + '...'
    }
    
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: undefined,
      prefillDescription: scriptText,
      characterName: characterName,
      shotId: props.shot.id
    })
  }
}

// Asset generation handlers
const handleGenerateShotImage = () => {
  if (props.shot.shotImage.status === 'GENERATING') return
  // 打开分镜图片生成面板
  panelManagerStore.openPanel('shot-image-generate', {
    shotId: props.shot.id,
    shotNo: props.shot.shotNo
  })
}

const handleGenerateVideo = () => {
  if (props.shot.video.status === 'GENERATING') return
  // 打开视频生成面板
  panelManagerStore.openPanel('video-generate', {
    shotId: props.shot.id,
    shotNo: props.shot.shotNo
  })
}

const handleConfirmGenerate = async (params: any) => {
  try {
    if (generateModalType.value === 'shot_image') {
      await editorStore.generateShotImage(props.shot.id, params)
    } else {
      await editorStore.generateVideo(props.shot.id, params)
    }
    showGenerateModal.value = false
  } catch (error) {
    console.error('[StoryboardRow] Generation failed:', error)
  }
}

// Asset version handlers
const handleViewShotImageVersions = async () => {
  if (!props.shot.shotImage.assetId) return
  try {
    versionModalAssetId.value = props.shot.shotImage.assetId
    assetVersions.value = await editorStore.getAssetVersions(props.shot.shotImage.assetId)
    showVersionModal.value = true
  } catch (error) {
    console.error('[StoryboardRow] Failed to load versions:', error)
  }
}

const handleViewVideoVersions = async () => {
  if (!props.shot.video.assetId) return
  try {
    versionModalAssetId.value = props.shot.video.assetId
    assetVersions.value = await editorStore.getAssetVersions(props.shot.video.assetId)
    showVersionModal.value = true
  } catch (error) {
    console.error('[StoryboardRow] Failed to load versions:', error)
  }
}

const handleSwitchVersion = async (versionId: number) => {
  if (!versionModalAssetId.value) return
  try {
    await editorStore.setAssetCurrentVersion(versionModalAssetId.value, { versionId })
    // Reload versions to update current marker
    assetVersions.value = await editorStore.getAssetVersions(versionModalAssetId.value)
  } catch (error) {
    console.error('[StoryboardRow] Failed to switch version:', error)
  }
}

const handleDownloadAsset = (url: string) => {
  window.open(url, '_blank')
}

// Combined click handler for AssetCell
const handleAssetClick = (assetType: 'shot_image' | 'video') => {
  const asset = assetType === 'shot_image' ? props.shot.shotImage : props.shot.video

  if (asset.status === 'READY') {
    // Show version history
    if (assetType === 'shot_image') {
      handleViewShotImageVersions()
    } else {
      handleViewVideoVersions()
    }
  } else if (asset.status === 'NONE' || asset.status === 'FAILED') {
    // Show generate modal
    if (assetType === 'shot_image') {
      handleGenerateShotImage()
    } else {
      handleGenerateVideo()
    }
  }
}

// 删除分镜图
const handleDeleteShotImage = async () => {
  console.log('[StoryboardRow] handleDeleteShotImage 被调用')
  console.log('[StoryboardRow] projectId:', editorStore.projectId)
  console.log('[StoryboardRow] shot:', props.shot)
  console.log('[StoryboardRow] shotImage:', props.shot.shotImage)
  
  if (!editorStore.projectId || !props.shot.shotImage.assetId) {
    console.log('[StoryboardRow] 缺少必要参数，退出')
    return
  }
  
  if (!confirm('删除后分镜图将变为“待生成”状态，是否继续？')) {
    console.log('[StoryboardRow] 用户取消删除')
    return
  }
  
  try {
    console.log('[StoryboardRow] 开始删除分镜图')
    await api.delete(`/projects/${editorStore.projectId}/shots/${props.shot.id}/assets/shot-image`)
    console.log('[StoryboardRow] 分镜图删除成功，刷新数据')
    await editorStore.fetchShots()
    window.$message?.success('已删除分镜图')
  } catch (error: any) {
    console.error('[StoryboardRow] 删除分镜图失败:', error)
    window.$message?.error(error.message || '删除失败')
  }
}

// 删除视频
const handleDeleteVideo = async () => {
  if (!editorStore.projectId || !props.shot.video.assetId) return
  
  if (!confirm('删除后视频将变为“待生成”状态，是否继续？')) return
  
  try {
    await api.delete(`/projects/${editorStore.projectId}/shots/${props.shot.id}/assets/video`)
    await editorStore.fetchShots()
    window.$message?.success('已删除视频')
  } catch (error: any) {
    console.error('[StoryboardRow] 删除视频失败:', error)
    window.$message?.error(error.message || '删除失败')
  }
}

// Character thumbnail click handler - 传递完整的角色信息
const handleCharacterClick = (characterId: number) => {
  // 从 shot.characters 获取绑定信息
  const boundChar = props.shot.characters.find(c => c.characterId === characterId)
  // 从 editorStore 获取完整的角色信息
  const fullChar = editorStore.characters.find(c => c.id === characterId)
  
  const hasThumbnail = fullChar?.thumbnailUrl || boundChar?.thumbnailUrl
  
  // 如果角色没有图片，传递剧本文本让AI解析
  if (!hasThumbnail) {
    let scriptText = editorStore.originalScript || props.shot.scriptText
    if (scriptText.length > 2000) {
      scriptText = scriptText.substring(0, 2000) + '...'
    }
    
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: characterId,
      characterName: fullChar ? ((fullChar as any).displayName || fullChar.name) : boundChar?.characterName,
      prefillDescription: scriptText,  // 让AI解析
      existingDescription: fullChar ? ((fullChar as any).finalDescription || (fullChar as any).description) : undefined
    })
  } else {
    // 已有图片，正常打开编辑面板
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: characterId,
      characterName: fullChar ? ((fullChar as any).displayName || fullChar.name) : boundChar?.characterName,
      existingThumbnailUrl: fullChar?.thumbnailUrl || boundChar?.thumbnailUrl,
      existingDescription: fullChar ? ((fullChar as any).finalDescription || (fullChar as any).description) : undefined
    })
  }
}

// Scene thumbnail click handler
const handleSceneClick = (sceneId: number) => {
  // 从 editorStore 获取完整的场景信息
  const scene = editorStore.scenes.find(s => s.id === sceneId)
  
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'scene',
    assetId: sceneId,
    sceneName: (scene as any)?.displayName || scene?.name,
    existingThumbnailUrl: scene?.thumbnailUrl,
    existingDescription: (scene as any)?.finalDescription || (scene as any)?.description
  })
}

// 点击新建场景 - 第一次带AI描述，后续自定义
const createSceneClickCount = ref(0)
const handleAddScene = () => {
  // 打开场景选择弹窗
  showSceneSelectModal.value = true
}

// 创建新场景
const handleCreateNewScene = () => {
  // 关闭场景选择弹窗
  showSceneSelectModal.value = false
  
  createSceneClickCount.value++
  
  // 第一次点击：带剧本内容让AI解析场景描述
  if (createSceneClickCount.value === 1) {
    let scriptText = editorStore.originalScript || props.shot.scriptText
    // 限制文本长度
    if (scriptText.length > 2000) {
      scriptText = scriptText.substring(0, 2000) + '...'
    }
    
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'scene',
      assetId: undefined,
      sceneName: '新场景',
      prefillDescription: scriptText,
      shotId: props.shot.id
    })
  } else {
    // 第二次及以后：描述为空（自定义）
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'scene',
      assetId: undefined,
      sceneName: '新场景',
      shotId: props.shot.id
    })
  }
  console.log('[StoryboardRow] Opening create scene panel for shot:', props.shot.id)
}

// Add character handler
const handleAddCharacter = () => {
  // 打开角色选择弹窗
  showCharacterSelectModal.value = true
}

// 选择角色后绑定到分镜
const handleCharacterSelected = async () => {
  showCharacterSelectModal.value = false
  // 刷新分镜数据
  await editorStore.fetchShots()
}

// 绑定角色到分镜
const handleBindCharacter = async (characterId: number) => {
  if (!editorStore.projectId) return
  try {
    await shotApi.createBinding(
      editorStore.projectId,
      props.shot.id,
      'PCHAR',
      characterId
    )
    showCharacterSelectModal.value = false
    window.$message?.success('角色绑定成功')
    await editorStore.fetchShots()
  } catch (error: any) {
    window.$message?.error(error.message || '绑定失败')
  }
}

// 删除角色（从项目中删除）
const deletingCharacterId = ref<number | null>(null)
const handleDeleteCharacter = async (characterId: number, event: Event) => {
  event.stopPropagation()
  if (!editorStore.projectId) return
  
  const character = editorStore.characters.find(c => c.id === characterId)
  if (!character) return
  
  const characterName = (character as any).displayName || character.name
  if (!confirm(`确定要删除角色「${characterName}」吗？`)) return
  
  deletingCharacterId.value = characterId
  try {
    await characterApi.deleteCharacter(editorStore.projectId, characterId)
    // 清除该角色的本地图片历史记录
    editorStore.clearLocalImageHistory('character', characterId)
    editorStore.clearLocalImageHistoryByName(characterName)
    window.$message?.success('角色已删除')
    await editorStore.fetchCharacters()
  } catch (error: any) {
    window.$message?.error(error.message || '删除失败')
  } finally {
    deletingCharacterId.value = null
  }
}

// 点击角色卡片 - 跳转到编辑面板
const handleCharacterCardClick = (character: any) => {
  // 如果角色没有图片，传递剧本文本让AI解析
  if (!character.thumbnailUrl) {
    let scriptText = editorStore.originalScript || props.shot.scriptText
    if (scriptText.length > 2000) {
      scriptText = scriptText.substring(0, 2000) + '...'
    }
    
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: character.id,
      characterName: (character as any).displayName || character.name,
      prefillDescription: scriptText,  // 让AI解析
      existingDescription: (character as any).finalDescription || (character as any).description
    })
  } else {
    // 已有图片，正常打开编辑面板
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: character.id,
      characterName: (character as any).displayName || character.name,
      existingThumbnailUrl: character.thumbnailUrl,
      existingDescription: (character as any).finalDescription || (character as any).description
    })
  }
}

// 点击新建角色 - 第一次带AI描述，后续自定义
const createCharacterClickCount = ref(0)
const handleCreateNewCharacter = () => {
  // 关闭角色选择弹窗
  showCharacterSelectModal.value = false
  
  createCharacterClickCount.value++
  
  // 第一次点击：带剧本内容让AI解析描述
  if (createCharacterClickCount.value === 1) {
    let scriptText = editorStore.originalScript || props.shot.scriptText
    // 限制文本长度
    if (scriptText.length > 2000) {
      scriptText = scriptText.substring(0, 2000) + '...'
    }
    
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: undefined,
      characterName: '新角色',
      prefillDescription: scriptText,
      shotId: props.shot.id
    })
  } else {
    // 第二次及以后：描述为空（自定义）
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: undefined,
      characterName: '新角色',
      shotId: props.shot.id
    })
  }
}

// 获取已绑定到当前分镜的角色（直接使用 shot.characters）
const boundCharacters = computed(() => {
  return props.shot.characters.map(boundChar => {
    // 从 editorStore 中查找完整的角色信息
    const fullChar = editorStore.characters.find(c => c.id === boundChar.characterId)
    return {
      ...boundChar,
      fullCharacter: fullChar
    }
  })
})

// 获取未绑定到当前分镜的角色
const unboundProjectCharacters = computed(() => {
  const boundIds = new Set(props.shot.characters.map(c => c.characterId))
  return editorStore.characters.filter(c => !boundIds.has(c.id))
})

// 解绑角色（从分镜中移除）
const unbindingCharacterId = ref<number | null>(null)
const handleUnbindCharacter = async (bindingId: number, characterName: string) => {
  if (!editorStore.projectId) return
  
  if (!confirm(`确定要将角色「${characterName}」从当前分镜中移除吗？`)) return
  
  unbindingCharacterId.value = bindingId
  try {
    await shotApi.deleteBinding(editorStore.projectId, props.shot.id, bindingId)
    window.$message?.success('角色已移除')
    await editorStore.fetchShots()
  } catch (error: any) {
    window.$message?.error(error.message || '移除失败')
  } finally {
    unbindingCharacterId.value = null
  }
}

// ============== 场景相关函数 ==============

// 获取未绑定到当前分镜的场景
const unboundProjectScenes = computed(() => {
  const boundSceneId = props.shot.scene?.sceneId
  return editorStore.scenes.filter(s => s.id !== boundSceneId)
})

// 绑定场景到分镜
const handleBindScene = async (sceneId: number) => {
  if (!editorStore.projectId) return
  
  console.log('[StoryboardRow] 绑定场景, sceneId:', sceneId, '当前场景:', props.shot.scene)
  
  // 如果已有场景，先解绑
  if (props.shot.scene) {
    try {
      console.log('[StoryboardRow] 先解绑旧场景, bindingId:', props.shot.scene.bindingId)
      await shotApi.deleteBinding(editorStore.projectId, props.shot.id, props.shot.scene.bindingId)
      console.log('[StoryboardRow] 解绑旧场景成功')
    } catch (error: any) {
      console.error('[StoryboardRow] 解绑旧场景失败:', error)
      window.$message?.error('更换场景失败，请先手动移除当前场景')
      return
    }
  }
  
  try {
    console.log('[StoryboardRow] 绑定新场景...')
    await shotApi.createBinding(
      editorStore.projectId,
      props.shot.id,
      'PSCENE',
      sceneId
    )
    console.log('[StoryboardRow] 绑定新场景成功')
    showSceneSelectModal.value = false
    window.$message?.success('场景绑定成功')
    await editorStore.fetchShots()
  } catch (error: any) {
    console.error('[StoryboardRow] 绑定新场景失败:', error)
    window.$message?.error(error.message || '绑定失败')
  }
}

// 解绑场景（从分镜中移除）
const unbindingSceneId = ref<number | null>(null)
const handleUnbindScene = async (bindingId: number, sceneName: string) => {
  if (!editorStore.projectId) return
  
  if (!confirm(`确定要将场景「${sceneName}」从当前分镜中移除吗？`)) return
  
  unbindingSceneId.value = bindingId
  try {
    await shotApi.deleteBinding(editorStore.projectId, props.shot.id, bindingId)
    window.$message?.success('场景已移除')
    await editorStore.fetchShots()
  } catch (error: any) {
    window.$message?.error(error.message || '移除失败')
  } finally {
    unbindingSceneId.value = null
  }
}

// 删除场景（从项目中删除）
const deletingSceneId = ref<number | null>(null)
const handleDeleteScene = async (sceneId: number, event: Event) => {
  event.stopPropagation()
  if (!editorStore.projectId) return
  
  const scene = editorStore.scenes.find(s => s.id === sceneId)
  if (!scene) return
  
  const sceneName = scene.displayName || scene.name
  if (!confirm(`确定要删除场景「${sceneName}」吗？`)) return
  
  deletingSceneId.value = sceneId
  try {
    await sceneApi.deleteScene(editorStore.projectId, sceneId)
    // 清除该场景的本地图片历史记录
    editorStore.clearLocalImageHistory('scene', sceneId)
    window.$message?.success('场景已删除')
    await editorStore.fetchScenes()
    await editorStore.fetchShots()
  } catch (error: any) {
    window.$message?.error(error.message || '删除失败')
  } finally {
    deletingSceneId.value = null
  }
}

// 点击场景卡片 - 跳转到编辑面板
const handleSceneCardClick = (scene: any) => {
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'scene',
    assetId: scene.id,
    sceneName: scene.displayName || scene.name,
    existingThumbnailUrl: scene.thumbnailUrl,
    existingDescription: (scene as any).finalDescription || (scene as any).description
  })
}

// 从表格中解绑角色（不需要确认）
const handleUnbindCharacterFromTable = async (bindingId: number, characterName: string) => {
  if (!editorStore.projectId) return
  
  try {
    await shotApi.deleteBinding(editorStore.projectId, props.shot.id, bindingId)
    window.$message?.success(`已移除 ${characterName}`)
    await editorStore.fetchShots()
  } catch (error: any) {
    window.$message?.error(error.message || '移除失败')
  }
}

// 从表格中解绑场景（不需要确认）
const handleUnbindSceneFromTable = async (bindingId: number, sceneName: string) => {
  if (!editorStore.projectId) return
  
  try {
    await shotApi.deleteBinding(editorStore.projectId, props.shot.id, bindingId)
    window.$message?.success(`已移除 ${sceneName}`)
    await editorStore.fetchShots()
  } catch (error: any) {
    window.$message?.error(error.message || '移除失败')
  }
}

// ============== 道具相关函数 ==============

// 添加道具处理 - 直接创建新道具（与场景功能一致）
const handleAddProp = () => {
  // 直接创建新道具，不打开选择弹窗
  handleCreateNewProp()
}

// 获取未绑定到当前分镜的道具
const unboundProjectProps = computed(() => {
  const boundPropIds = new Set(props.shot.props.map(p => p.propId))
  return editorStore.props?.filter((p: any) => !boundPropIds.has(p.id)) || []
})

// 绑定道具到分镜
const handleBindProp = async (propId: number) => {
  if (!editorStore.projectId) return
  
  console.log('[StoryboardRow] 绑定道具, propId:', propId)
  
  try {
    console.log('[StoryboardRow] 绑定新道具...')
    await shotApi.createBinding(
      editorStore.projectId,
      props.shot.id,
      'PPROP',
      propId
    )
    console.log('[StoryboardRow] 绑定新道具成功')
    showPropSelectModal.value = false
    window.$message?.success('道具绑定成功')
    await editorStore.fetchShots()
  } catch (error: any) {
    console.error('[StoryboardRow] 绑定新道具失败:', error)
    window.$message?.error(error.message || '绑定失败')
  }
}

// 点击道具卡片 - 跳转到编辑面板
const handlePropCardClick = (prop: any) => {
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'prop',
    assetId: prop.id,
    propName: prop.displayName || prop.name,
    existingThumbnailUrl: prop.thumbnailUrl,
    existingDescription: (prop as any).finalDescription || (prop as any).description
  })
}

// 点击分镜行中的道具 - 跳转到编辑面板
const handlePropClick = (propId: number) => {
  const prop = editorStore.props?.find((p: any) => p.id === propId)
  if (prop) {
    handlePropCardClick(prop)
  }
}

// 创建新道具
const handleCreateNewProp = () => {
  // 关闭道具选择弹窗
  showPropSelectModal.value = false
  
  // 默认不自动分析，改为用户自定义（参考角色和场景的处理方式）
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'prop',
    assetId: undefined,
    propName: '新道具',
    shotId: props.shot.id
  })
  console.log('[StoryboardRow] Opening create prop panel for shot:', props.shot.id)
}

// 从表格中解绑道具（不需要确认）
const handleUnbindPropFromTable = async (bindingId: number, propName: string) => {
  if (!editorStore.projectId) return
  
  try {
    await shotApi.deleteBinding(editorStore.projectId, props.shot.id, bindingId)
    window.$message?.success(`已移除 ${propName}`)
    await editorStore.fetchShots()
  } catch (error: any) {
    window.$message?.error(error.message || '移除失败')
  }
}

// 下载图片
const handleDownloadThumbnail = async (url: string, name: string) => {
  try {
    const response = await fetch(url)
    const blob = await response.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `${name}.jpg`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)
    window.$message?.success('下载成功')
  } catch (error) {
    console.error('下载失败:', error)
    window.$message?.error('下载失败')
  }
}

// 复制图片到剪贴板
const handleCopyThumbnail = async (url: string) => {
  try {
    const response = await fetch(url)
    const blob = await response.blob()
    await navigator.clipboard.write([
      new ClipboardItem({
        [blob.type]: blob
      })
    ])
    window.$message?.success('已复制到剪贴板')
  } catch (error) {
    console.error('复制失败:', error)
    window.$message?.error('复制失败')
  }
}
</script>

<template>
  <tr
    :class="[
      'group transition-colors border-b border-border-subtle',
      selected ? 'bg-bg-subtle border-l-2 !border-l-[#00FFCC]' : 'hover:bg-bg-subtle',
    ]"
  >
    <!-- Checkbox -->
    <td class="px-3 py-3 w-[48px]">
      <input
        type="checkbox"
        :checked="selected"
        @change="onToggleSelect"
        class="w-4 h-4 rounded bg-bg-hover border-border-default text-text-primary focus:ring-2 focus:ring-[#00FFCC]/50 cursor-pointer"
      >
    </td>

    <!-- Shot ID -->
    <td class="px-3 py-3 w-[80px]">
      <span class="inline-flex items-center justify-center px-2.5 py-1 bg-bg-hover text-text-secondary text-sm font-semibold rounded">
        {{ shot.shotNo }}
      </span>
    </td>

    <!-- Script Text (Editable) -->
    <td class="px-3 py-3 flex-1 min-w-[200px] max-w-[400px]">
      <!-- Display Mode -->
      <div
        class="text-text-primary text-sm leading-relaxed cursor-pointer hover:bg-bg-subtle px-2 py-1 rounded-lg transition-colors whitespace-pre-wrap break-words"
        @dblclick="startEditing"
        title="双击编辑"
      >
        {{ displayScript }}
      </div>
    </td>

    <!-- Characters -->
    <td class="px-3 py-3 w-[140px]">
      <div class="flex flex-col gap-1">
        <!-- 已绑定的角色（胶囊样式：头像+名字） -->
        <div
          v-for="char in shot.characters.slice(0, 3)"
          :key="char.bindingId"
          class="relative group/bound"
        >
          <div
            class="flex items-center gap-1.5 px-2 py-1 rounded bg-bg-hover cursor-pointer hover:bg-bg-subtle transition-colors h-8"
            @click="handleCharacterClick(char.characterId)"
            :title="char.characterName"
          >
            <!-- 缩略图容器 -->
            <div class="relative group/thumbnail">
              <img
                v-if="char.thumbnailUrl"
                :src="char.thumbnailUrl"
                :alt="char.characterName"
                class="w-6 h-6 rounded object-cover flex-shrink-0"
              >
              <div
                v-else
                class="w-6 h-6 rounded bg-bg-subtle flex items-center justify-center text-text-tertiary text-xs font-bold flex-shrink-0"
              >
                {{ char.characterName?.[0] || '?' }}
              </div>
              <!-- 悬浮按钮（仅在有缩略图时显示） -->
              <div
                v-if="char.thumbnailUrl"
                class="absolute inset-0 bg-gray-800 rounded opacity-0 group-hover/thumbnail:opacity-100 transition-opacity flex items-center justify-center gap-0.5"
                @click.stop
              >
                <button
                  @click.stop="handleDownloadThumbnail(char.thumbnailUrl, char.characterName)"
                  class="p-0.5 rounded hover:bg-bg-hover transition-colors"
                  title="下载"
                >
                  <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                  </svg>
                </button>
                <button
                  @click.stop="handleCopyThumbnail(char.thumbnailUrl)"
                  class="p-0.5 rounded hover:bg-bg-hover transition-colors"
                  title="复制"
                >
                  <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                </button>
              </div>
            </div>
            <span class="text-text-secondary text-xs font-medium truncate flex-1">{{ char.characterName }}</span>
          </div>
          <!-- 解绑按钮（悬浮显示） -->
          <button
            @click.stop="handleUnbindCharacterFromTable(char.bindingId, char.characterName)"
            class="absolute -top-1 -right-1 w-4 h-4 rounded bg-red-500/80 flex items-center justify-center hover:bg-red-500 transition-all opacity-0 group-hover/bound:opacity-100 z-10"
            title="移除角色"
          >
            <svg class="w-2.5 h-2.5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <!-- 更多角色数量提示 -->
        <span v-if="shot.characters.length > 3" class="text-text-tertiary text-xs h-8 flex items-center justify-center">
          +{{ shot.characters.length - 3 }}更多
        </span>
        <!-- 从剧本提取的人物 -->
        <div
          v-for="charName in unboundCharacters.slice(0, 2)"
          :key="charName"
          class="relative group/char"
        >
          <div
            v-if="getCharacterByName(charName)?.thumbnailUrl"
            class="flex items-center gap-1.5 px-2 py-1 rounded bg-bg-hover cursor-pointer hover:bg-bg-subtle transition-colors h-8"
            @click="handleGenerateCharacterFromScript(charName)"
            :title="charName"
          >
            <!-- 缩略图容器 -->
            <div class="relative group/thumbnail">
              <img
                :src="getCharacterByName(charName)?.thumbnailUrl"
                :alt="charName"
                class="w-6 h-6 rounded object-cover"
              >
              <!-- 悬浮按钮 -->
              <div
                class="absolute inset-0 bg-gray-800 rounded opacity-0 group-hover/thumbnail:opacity-100 transition-opacity flex items-center justify-center gap-0.5"
                @click.stop
              >
                <button
                  @click.stop="handleDownloadThumbnail(getCharacterByName(charName)!.thumbnailUrl!, charName)"
                  class="p-0.5 rounded hover:bg-bg-hover transition-colors"
                  title="下载"
                >
                  <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                  </svg>
                </button>
                <button
                  @click.stop="handleCopyThumbnail(getCharacterByName(charName)!.thumbnailUrl!)"
                  class="p-0.5 rounded hover:bg-bg-hover transition-colors"
                  title="复制"
                >
                  <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                </button>
              </div>
            </div>
            <span class="text-text-secondary text-xs font-medium truncate flex-1">{{ charName }}</span>
          </div>
          <!-- 否则显示标签 -->
          <div
            v-else
            @click="handleGenerateCharacterFromScript(charName)"
            class="flex items-center gap-1.5 px-2 py-1 rounded bg-bg-hover cursor-pointer hover:bg-bg-subtle transition-colors h-8"
            :title="`点击生成角色: ${charName}`"
          >
            <div class="w-6 h-6 rounded bg-bg-subtle flex items-center justify-center text-text-tertiary text-xs font-bold flex-shrink-0">
              {{ charName?.[0] || '?' }}
            </div>
            <span class="text-text-secondary text-xs truncate flex-1">{{ charName }}</span>
          </div>
          <!-- 删除按钮（悬浮显示） -->
          <button
            @click.stop="handleDismissCharacter(charName)"
            class="absolute -top-1 -right-1 w-4 h-4 rounded bg-red-500/80 flex items-center justify-center hover:bg-red-500 transition-all opacity-0 group-hover/char:opacity-100 z-10"
            title="删除"
          >
            <svg class="w-2.5 h-2.5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <!-- 添加角色按钮 -->
        <button
          @click="handleAddCharacter"
          class="h-8 rounded border-2 border-dashed border-border-default bg-transparent flex items-center justify-center gap-1 hover:bg-bg-subtle transition-colors"
          title="添加角色"
        >
          <svg class="w-4 h-4 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
          </svg>
          <span class="text-text-tertiary text-xs">添加</span>
        </button>
      </div>
    </td>

    <!-- Scene -->
    <td class="px-3 py-3 w-[120px]">
      <div class="flex flex-col gap-1">
        <!-- 已绑定的场景 -->
        <div
          v-if="shot.scene"
          class="relative group/bound"
        >
          <div
            class="flex items-center gap-1.5 px-2 py-1 rounded bg-bg-hover cursor-pointer hover:bg-bg-subtle transition-colors h-8"
            @click="handleSceneClick(shot.scene.sceneId)"
            :title="shot.scene.sceneName"
          >
            <!-- 缩略图容器 -->
            <div class="relative group/thumbnail">
              <img
                v-if="shot.scene.thumbnailUrl"
                :src="shot.scene.thumbnailUrl"
                :alt="shot.scene.sceneName"
                class="w-6 h-6 rounded object-cover flex-shrink-0"
              >
              <div
                v-else
                class="w-6 h-6 rounded bg-bg-subtle flex items-center justify-center text-text-tertiary text-xs flex-shrink-0"
              >
                🏞️
              </div>
              <!-- 悬浮按钮（仅在有缩略图时显示） -->
              <div
                v-if="shot.scene.thumbnailUrl"
                class="absolute inset-0 bg-gray-800 rounded opacity-0 group-hover/thumbnail:opacity-100 transition-opacity flex items-center justify-center gap-0.5"
                @click.stop
              >
                <button
                  @click.stop="handleDownloadThumbnail(shot.scene.thumbnailUrl, shot.scene.sceneName)"
                  class="p-0.5 rounded hover:bg-bg-hover transition-colors"
                  title="下载"
                >
                  <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                  </svg>
                </button>
                <button
                  @click.stop="handleCopyThumbnail(shot.scene.thumbnailUrl)"
                  class="p-0.5 rounded hover:bg-bg-hover transition-colors"
                  title="复制"
                >
                  <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                </button>
              </div>
            </div>
            <span class="text-text-secondary text-xs font-medium truncate flex-1">{{ shot.scene.sceneName }}</span>
          </div>
          <!-- 解绑按钮（悬浮显示） -->
          <button
            @click.stop="handleUnbindSceneFromTable(shot.scene.bindingId, shot.scene.sceneName)"
            class="absolute -top-1 -right-1 w-4 h-4 rounded bg-red-500/80 flex items-center justify-center hover:bg-red-500 transition-all opacity-0 group-hover/bound:opacity-100 z-10"
            title="移除场景"
          >
            <svg class="w-2.5 h-2.5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <!-- 添加场景按钮 -->
        <button
          @click="handleAddScene"
          class="h-8 rounded border-2 border-dashed border-border-default bg-transparent flex items-center justify-center gap-1 hover:bg-bg-subtle transition-colors"
          title="添加场景"
        >
          <svg class="w-4 h-4 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
          </svg>
          <span class="text-text-tertiary text-xs">添加</span>
        </button>
      </div>
    </td>

    <!-- Props (Hidden) -->
    <td class="w-0 hidden"></td>

    <!-- Prop 道具画像 -->
    <td class="px-3 py-3 w-[120px]">
      <div class="flex flex-col gap-1">
        <!-- 已绑定的道具 -->
        <div
          v-for="prop in shot.props.slice(0, 3)"
          :key="prop.bindingId"
          class="relative group/bound"
        >
          <div
            class="flex items-center gap-1.5 px-2 py-1 rounded bg-bg-hover cursor-pointer hover:bg-bg-subtle transition-colors h-8"
            @click="handlePropClick(prop.propId)"
            :title="prop.propName"
          >
            <!-- 缩略图容器 -->
            <div class="relative group/thumbnail">
              <img
                v-if="prop.thumbnailUrl"
                :src="prop.thumbnailUrl"
                :alt="prop.propName"
                class="w-6 h-6 rounded object-cover flex-shrink-0"
              >
              <div
                v-else
                class="w-6 h-6 rounded bg-bg-subtle flex items-center justify-center text-text-tertiary text-xs flex-shrink-0"
              >
                🔧
              </div>
              <!-- 悬浮按钮（仅在有缩略图时显示） -->
              <div
                v-if="prop.thumbnailUrl"
                class="absolute inset-0 bg-gray-800 rounded opacity-0 group-hover/thumbnail:opacity-100 transition-opacity flex items-center justify-center gap-0.5"
                @click.stop
              >
                <button
                  @click.stop="handleDownloadThumbnail(prop.thumbnailUrl, prop.propName)"
                  class="p-0.5 rounded hover:bg-bg-hover transition-colors"
                  title="下载"
                >
                  <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                  </svg>
                </button>
                <button
                  @click.stop="handleCopyThumbnail(prop.thumbnailUrl)"
                  class="p-0.5 rounded hover:bg-bg-hover transition-colors"
                  title="复制"
                >
                  <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                </button>
              </div>
            </div>
            <span class="text-text-secondary text-xs font-medium truncate flex-1">{{ prop.propName }}</span>
          </div>
          <!-- 解绑按钮（悬浮显示） -->
          <button
            @click.stop="handleUnbindPropFromTable(prop.bindingId, prop.propName)"
            class="absolute -top-1 -right-1 w-4 h-4 rounded bg-red-500/80 flex items-center justify-center hover:bg-red-500 transition-all opacity-0 group-hover/bound:opacity-100 z-10"
            title="移除道具"
          >
            <svg class="w-2.5 h-2.5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <!-- 添加道具按钮 -->
        <button
          @click="handleAddProp"
          class="h-8 rounded border-2 border-dashed border-border-default bg-transparent flex items-center justify-center gap-1 hover:bg-bg-subtle transition-colors"
          title="添加道具"
        >
          <svg class="w-4 h-4 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
          </svg>
          <span class="text-text-tertiary text-xs">添加</span>
        </button>
      </div>
    </td>

    <!-- Shot Image Asset -->
    <td class="px-3 py-3 w-[120px]">
      <AssetCell
        :asset="shot.shotImage"
        label="分镜图"
        :on-click="() => handleAssetClick('shot_image')"
        :on-delete="shot.shotImage.status === 'READY' ? handleDeleteShotImage : undefined"
      />
    </td>

    <!-- Video Asset -->
    <td class="px-3 py-3 w-[120px]">
      <AssetCell
        :asset="shot.video"
        label="视频"
        :on-click="() => handleAssetClick('video')"
        :on-delete="shot.video.status === 'READY' ? handleDeleteVideo : undefined"
      />
    </td>

    <!-- Actions -->
    <td class="px-3 py-3 w-[140px]">
      <div class="flex items-center gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
        <!-- 向上合并 -->
        <button
          :disabled="isFirst"
          :class="[
            'p-1.5 rounded-lg transition-colors',
            isFirst
              ? 'bg-bg-subtle cursor-not-allowed opacity-30'
              : 'bg-bg-hover hover:bg-bg-hover'
          ]"
          title="向上合并"
          @click="onMergeUp"
        >
          <svg class="w-4 h-4 text-text-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 15l7-7 7 7"></path>
          </svg>
        </button>
        <!-- 向下合并 -->
        <button
          :disabled="isLast"
          :class="[
            'p-1.5 rounded-lg transition-colors',
            isLast
              ? 'bg-bg-subtle cursor-not-allowed opacity-30'
              : 'bg-bg-hover hover:bg-bg-hover'
          ]"
          title="向下合并"
          @click="onMergeDown"
        >
          <svg class="w-4 h-4 text-text-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
          </svg>
        </button>
        <!-- 删除 -->
        <button
          class="p-1.5 bg-red-500/20 rounded-lg hover:bg-red-500/30 transition-colors"
          title="删除分镜"
          @click="onDelete"
        >
          <svg class="w-4 h-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
          </svg>
        </button>
      </div>
    </td>
  </tr>

  <!-- Asset Generation Modal -->
  <AssetGenerateModal
    :show="showGenerateModal"
    :asset-type="generateModalType"
    :asset-id="shot.id"
    :on-close="() => (showGenerateModal = false)"
    :on-confirm="handleConfirmGenerate"
  />

  <!-- Asset Version History Modal -->
  <AssetVersionModal
    :show="showVersionModal"
    :asset-id="versionModalAssetId"
    :versions="assetVersions"
    :on-close="() => (showVersionModal = false)"
    :on-switch-version="handleSwitchVersion"
    :on-download="handleDownloadAsset"
  />

  <!-- Script Edit Modal -->
  <Teleport to="body">
    <!-- 角色选择弹窗 -->
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="showCharacterSelectModal"
        class="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none"
        @click.self="showCharacterSelectModal = false"
      >
        <div class="bg-bg-elevated w-[800px] max-h-[70vh] rounded shadow-2xl flex flex-col pointer-events-auto">
          <!-- Header -->
          <div class="flex items-center justify-between p-4 border-b border-border-default">
            <h3 class="text-text-primary text-base font-semibold">选择角色绑定到分镜 #{{ shot.shotNo }}</h3>
            <button
              class="p-1.5 rounded-lg hover:bg-bg-hover transition-colors"
              @click="showCharacterSelectModal = false"
            >
              <svg class="w-5 h-5 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
          
          <!-- Content -->
          <div class="flex-1 overflow-y-auto p-4 space-y-6">
            <!-- 作品中角色 -->
            <div>
              <h4 class="text-text-primary text-sm font-semibold mb-3">作品中角色 <span class="text-text-tertiary font-normal">({{ boundCharacters.length }})</span></h4>
              <!-- 空状态提示 -->
              <div v-if="boundCharacters.length === 0" class="flex items-center justify-center py-8 text-text-tertiary text-sm">
                <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                当前分镜还没有绑定角色，请从下方选择
              </div>
              <!-- 角色列表 -->
              <div v-else class="grid grid-cols-4 gap-3">
                <!-- 已绑定角色卡片 -->
                <div
                  v-for="boundChar in boundCharacters"
                  :key="boundChar.bindingId"
                  class="group relative flex flex-col items-center cursor-pointer rounded transition-all"
                >
                  <!-- 图片容器 -->
                  <div class="relative w-full aspect-square mb-2" @click="boundChar.fullCharacter && handleCharacterCardClick(boundChar.fullCharacter)">
                    <img
                      v-if="boundChar.thumbnailUrl"
                      :src="boundChar.thumbnailUrl"
                      :alt="boundChar.characterName"
                      class="w-full h-full rounded object-cover transition-transform group-hover:scale-105"
                    >
                    <div v-else class="w-full h-full rounded bg-bg-hover flex items-center justify-center">
                      <svg class="w-8 h-8 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
                      </svg>
                    </div>
                    <!-- 已在作品中标记 -->
                    <div class="absolute top-2 right-2 w-3 h-3 bg-gray-900 rounded border-2 border-[#1E2025] shadow-[0_0_6px_2px_rgba(0,255,204,0.7)]"></div>
                    
                    <!-- 悬浮操作层（移除按钮） -->
                    <button
                      class="absolute top-2 left-2 p-1.5 rounded-lg bg-red-500/80 hover:bg-red-500 transition-all opacity-0 group-hover:opacity-100"
                      :disabled="unbindingCharacterId === boundChar.bindingId"
                      @click.stop="handleUnbindCharacter(boundChar.bindingId, boundChar.characterName)"
                      title="移除角色"
                    >
                      <svg v-if="unbindingCharacterId !== boundChar.bindingId" class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                      </svg>
                      <svg v-else class="w-4 h-4 text-white animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                      </svg>
                    </button>
                  </div>
                  <!-- 角色名称 -->
                  <span class="text-text-secondary text-xs text-center truncate w-full px-1">{{ boundChar.characterName }}</span>
                </div>
              </div>
            </div>

            <!-- 全部可用角色 -->
            <div>
              <h4 class="text-text-primary text-sm font-semibold mb-3">全部可用角色 <span class="text-text-tertiary font-normal">({{ unboundProjectCharacters.length }})</span></h4>
              <div class="grid grid-cols-4 gap-3">
                <!-- 未绑定角色卡片 -->
                <div
                  v-for="char in unboundProjectCharacters"
                  :key="char.id"
                  class="group relative flex flex-col items-center cursor-pointer rounded transition-all"
                >
                  <!-- 图片容器 -->
                  <div class="relative w-full aspect-square mb-2" @click="handleCharacterCardClick(char)">
                    <img
                      v-if="char.thumbnailUrl"
                      :src="char.thumbnailUrl"
                      :alt="(char as any).displayName || char.name"
                      class="w-full h-full rounded object-cover transition-transform group-hover:scale-105"
                    >
                    <div v-else class="w-full h-full rounded bg-bg-hover flex items-center justify-center">
                      <svg class="w-8 h-8 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
                      </svg>
                    </div>
                    
                    <!-- 悬浮操作层 -->
                    <div class="absolute inset-x-0 bottom-0 p-2 flex justify-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        class="px-3 py-1.5 rounded-lg bg-gray-900/90 text-white text-xs font-medium hover:bg-gray-800 transition-colors"
                        @click.stop="handleBindCharacter(char.id)"
                      >
                        + 使用角色
                      </button>
                    </div>
                    <!-- 删除按钮 -->
                    <button
                      class="absolute top-2 right-2 p-1.5 rounded-lg bg-red-500/80 hover:bg-red-500 transition-all opacity-0 group-hover:opacity-100"
                      :disabled="deletingCharacterId === char.id"
                      @click.stop="handleDeleteCharacter(char.id, $event)"
                      title="删除角色"
                    >
                      <svg class="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                      </svg>
                    </button>
                  </div>
                  
                  <!-- 角色名称 -->
                  <span class="text-text-secondary text-xs text-center truncate w-full px-1">{{ (char as any).displayName || char.name }}</span>
                </div>
                
                <!-- 新建角色按钮 -->
                <div
                  class="flex flex-col items-center cursor-pointer rounded transition-all hover:bg-bg-subtle"
                  @click="handleCreateNewCharacter"
                >
                  <div class="relative w-full aspect-square mb-2 rounded border-2 border-dashed border-border-default bg-bg-subtle flex items-center justify-center hover:border-gray-900/50 transition-colors">
                    <svg class="w-8 h-8 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
                    </svg>
                  </div>
                  <span class="text-text-tertiary text-xs text-center">创建</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 场景选择弹窗 -->
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="showSceneSelectModal"
        class="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none"
        @click.self="showSceneSelectModal = false"
      >
        <div class="bg-bg-elevated w-[800px] max-h-[70vh] rounded shadow-2xl flex flex-col pointer-events-auto">
          <!-- Header -->
          <div class="flex items-center justify-between p-4 border-b border-border-default">
            <h3 class="text-text-primary text-base font-semibold">选择场景绑定到分镜 #{{ shot.shotNo }}</h3>
            <button
              class="p-1.5 rounded-lg hover:bg-bg-hover transition-colors"
              @click="showSceneSelectModal = false"
            >
              <svg class="w-5 h-5 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
          
          <!-- Content -->
          <div class="flex-1 overflow-y-auto p-4 space-y-6">
            <!-- 当前分镜场景 -->
            <div>
              <h4 class="text-text-primary text-sm font-semibold mb-3">当前分镜场景 <span class="text-text-tertiary font-normal">({{ shot.scene ? 1 : 0 }})</span></h4>
              <!-- 空状态提示 -->
              <div v-if="!shot.scene" class="flex items-center justify-center py-8 text-text-tertiary text-sm">
                <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                当前分镜还没有绑定场景，请从下方选择
              </div>
              <!-- 已绑定场景 -->
              <div v-else class="grid grid-cols-4 gap-3">
                <div
                  class="group relative flex flex-col items-center cursor-pointer rounded transition-all"
                >
                  <!-- 图片容器 -->
                  <div class="relative w-full aspect-square mb-2" @click="handleSceneCardClick(editorStore.scenes.find(s => s.id === shot.scene.sceneId))">
                    <img
                      v-if="shot.scene.thumbnailUrl"
                      :src="shot.scene.thumbnailUrl"
                      :alt="shot.scene.sceneName"
                      class="w-full h-full rounded object-cover transition-transform group-hover:scale-105"
                    >
                    <div v-else class="w-full h-full rounded bg-bg-hover flex items-center justify-center">
                      <span class="text-3xl">🏞️</span>
                    </div>
                    <!-- 已在作品中标记 -->
                    <div class="absolute top-2 right-2 w-3 h-3 bg-gray-900 rounded border-2 border-[#1E2025] shadow-[0_0_6px_2px_rgba(0,255,204,0.7)]"></div>
                    
                    <!-- 悬浮操作层（移除按钮） -->
                    <button
                      class="absolute top-2 left-2 p-1.5 rounded-lg bg-red-500/80 hover:bg-red-500 transition-all opacity-0 group-hover:opacity-100"
                      :disabled="unbindingSceneId === shot.scene.bindingId"
                      @click.stop="handleUnbindScene(shot.scene.bindingId, shot.scene.sceneName)"
                      title="移除场景"
                    >
                      <svg v-if="unbindingSceneId !== shot.scene.bindingId" class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                      </svg>
                      <svg v-else class="w-4 h-4 text-white animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                      </svg>
                    </button>
                  </div>
                  <!-- 场景名称 -->
                  <span class="text-text-secondary text-xs text-center truncate w-full px-1">{{ shot.scene.sceneName }}</span>
                </div>
              </div>
            </div>

            <!-- 全部可用场景 -->
            <div>
              <h4 class="text-text-primary text-sm font-semibold mb-3">全部可用场景 <span class="text-text-tertiary font-normal">({{ unboundProjectScenes.length }})</span></h4>
              <div class="grid grid-cols-4 gap-3">
                <!-- 未绑定场景卡片 -->
                <div
                  v-for="scene in unboundProjectScenes"
                  :key="scene.id"
                  class="group relative flex flex-col items-center cursor-pointer rounded transition-all"
                >
                  <!-- 图片容器 -->
                  <div class="relative w-full aspect-square mb-2" @click="handleSceneCardClick(scene)">
                    <img
                      v-if="scene.thumbnailUrl"
                      :src="scene.thumbnailUrl"
                      :alt="scene.displayName || scene.name"
                      class="w-full h-full rounded object-cover transition-transform group-hover:scale-105"
                    >
                    <div v-else class="w-full h-full rounded bg-bg-hover flex items-center justify-center">
                      <span class="text-3xl">🏞️</span>
                    </div>
                    
                    <!-- 悬浮操作层 -->
                    <div class="absolute inset-x-0 bottom-0 p-2 flex justify-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        class="px-3 py-1.5 rounded-lg bg-gray-900/90 text-white text-xs font-medium hover:bg-gray-800 transition-colors"
                        @click.stop="handleBindScene(scene.id)"
                      >
                        + 使用场景
                      </button>
                    </div>
                    <!-- 删除按钮 -->
                    <button
                      class="absolute top-2 right-2 p-1.5 rounded-lg bg-red-500/80 hover:bg-red-500 transition-all opacity-0 group-hover:opacity-100"
                      :disabled="deletingSceneId === scene.id"
                      @click.stop="handleDeleteScene(scene.id, $event)"
                      title="删除场景"
                    >
                      <svg class="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                      </svg>
                    </button>
                  </div>
                  
                  <!-- 场景名称 -->
                  <span class="text-text-secondary text-xs text-center truncate w-full px-1">{{ scene.displayName || scene.name }}</span>
                </div>
                
                <!-- 新建场景按钮 -->
                <div
                  class="flex flex-col items-center cursor-pointer rounded transition-all hover:bg-bg-subtle"
                  @click="handleCreateNewScene"
                >
                  <div class="relative w-full aspect-square mb-2 rounded border-2 border-dashed border-border-default bg-bg-subtle flex items-center justify-center hover:border-gray-900/50 transition-colors">
                    <svg class="w-8 h-8 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
                    </svg>
                  </div>
                  <span class="text-text-tertiary text-xs text-center">创建</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 道具选择弹窗 -->
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="showPropSelectModal"
        class="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none"
        @click.self="showPropSelectModal = false"
      >
        <div class="bg-bg-elevated w-[800px] max-h-[70vh] rounded shadow-2xl flex flex-col pointer-events-auto">
          <!-- Header -->
          <div class="flex items-center justify-between p-4 border-b border-border-default">
            <h3 class="text-text-primary text-base font-semibold">选择道具绑定到分镜 #{{ shot.shotNo }}</h3>
            <button
              class="p-1.5 rounded-lg hover:bg-bg-hover transition-colors"
              @click="showPropSelectModal = false"
            >
              <svg class="w-5 h-5 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
          
          <!-- Content -->
          <div class="flex-1 overflow-y-auto p-4 space-y-6">
            <!-- 全部可用道具 -->
            <div>
              <h4 class="text-text-primary text-sm font-semibold mb-3">全部可用道具 <span class="text-text-tertiary font-normal">({{ unboundProjectProps.length }})</span></h4>
              <div v-if="unboundProjectProps.length === 0" class="flex items-center justify-center py-8 text-text-tertiary text-sm">
                <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                暂无可用道具，请先创建新道具
              </div>
              <div v-else class="grid grid-cols-4 gap-3">
                <!-- 道具卡片 -->
                <div
                  v-for="prop in unboundProjectProps"
                  :key="prop.id"
                  class="group relative flex flex-col items-center cursor-pointer rounded transition-all hover:bg-bg-subtle"
                >
                  <div class="relative w-full aspect-square mb-2" @click="handlePropCardClick(prop)">
                    <img
                      v-if="prop.thumbnailUrl"
                      :src="prop.thumbnailUrl"
                      :alt="prop.displayName || prop.name"
                      class="w-full h-full rounded object-cover transition-transform group-hover:scale-105"
                    >
                    <div v-else class="w-full h-full rounded bg-bg-hover flex items-center justify-center">
                      <svg class="w-8 h-8 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"></path>
                      </svg>
                    </div>
                    
                    <!-- 悬浮操作层 -->
                    <div class="absolute inset-x-0 bottom-0 p-2 flex justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        class="px-3 py-1.5 rounded-lg bg-gray-900/90 text-white text-xs font-medium hover:bg-gray-800 transition-colors"
                        @click.stop="handleBindProp(prop.id)"
                      >
                        + 使用道具
                      </button>
                    </div>
                  </div>
                  
                  <!-- 道具名称 -->
                  <span class="text-text-secondary text-xs text-center truncate w-full px-1">{{ prop.displayName || prop.name }}</span>
                </div>
                
                <!-- 新建道具按钮 -->
                <div
                  class="flex flex-col items-center cursor-pointer rounded transition-all hover:bg-bg-subtle"
                  @click="handleCreateNewProp"
                >
                  <div class="relative w-full aspect-square mb-2 rounded border-2 border-dashed border-border-default bg-bg-subtle flex items-center justify-center hover:border-gray-900/50 transition-colors">
                    <svg class="w-8 h-8 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
                    </svg>
                  </div>
                  <span class="text-text-tertiary text-xs text-center">创建</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Script Edit Modal -->
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="isEditing"
        class="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none"
        @click.self="saveScript"
      >
        <div class="bg-bg-elevated w-[600px] rounded p-5 shadow-2xl pointer-events-auto">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-text-primary text-base font-semibold">编辑分镜 #{{ shot.shotNo }}</h3>
            <button
              class="p-1.5 rounded-lg hover:bg-bg-hover transition-colors"
              @click="cancelEditing"
            >
              <svg class="w-5 h-5 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
          
          <textarea
            v-model="editingText"
            class="w-full h-64 px-4 py-3 bg-bg-base border border-border-default rounded text-text-primary text-sm resize-none focus:outline-none focus:ring-2 focus:ring-[#00FFCC]/50 whitespace-pre-wrap leading-relaxed"
            :maxlength="maxLength"
            autofocus
            @keydown.enter.ctrl="saveScript"
            @keydown.esc="cancelEditing"
          ></textarea>
          
          <div class="flex items-center justify-between mt-3">
            <span class="text-text-tertiary text-xs">{{ editingText.length }} / {{ maxLength }}</span>
            <div class="flex items-center gap-2">
              <button
                class="px-4 py-1.5 bg-bg-hover text-text-tertiary text-sm rounded hover:bg-bg-hover transition-colors"
                @click="cancelEditing"
              >
                取消
              </button>
              <button
                class="px-4 py-1.5 bg-bg-subtle text-text-secondary font-medium text-sm rounded hover:bg-bg-hover transition-colors"
                @click="saveScript"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
