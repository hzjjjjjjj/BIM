<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

export interface BodyProfile {
  gender: '男' | '女'
  heightCm: number
  weightKg: number
  bmi: number
  bodyFat: number
  waistCm: number
  muscleRate: number
  bodyType: string
  bodyAge: number
}

interface Props {
  profile: BodyProfile | null
  showFat?: boolean
  autoRotate?: boolean
}
const props = withDefaults(defineProps<Props>(), { showFat: false, autoRotate: true })

const canvasHost = ref<HTMLDivElement | null>(null)
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let frameId = 0
let bodyGroup: THREE.Group | null = null
let fatGroup: THREE.Group | null = null
let resizeObserver: ResizeObserver | null = null

const CAM_POS: [number, number, number] = [0, 1.15, 3.3]
const TARGET: [number, number, number] = [0, 1.0, 0]

function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v))
}

function disposeObject(obj: THREE.Object3D): void {
  obj.traverse((child) => {
    const mesh = child as THREE.Mesh
    if (mesh.geometry) mesh.geometry.dispose()
    if (mesh.material) {
      const m = mesh.material
      if (Array.isArray(m)) m.forEach((x) => x.dispose())
      else m.dispose()
    }
  })
}

// 由健康指标推导的人体孪生：头/颈/躯干/腹/胸/肩/四肢/足
function buildBody(p: BodyProfile): THREE.Group {
  const g = new THREE.Group()
  const H = Math.max(1.4, p.heightCm / 100) // 米即场景单位，脚底 y=0
  const skin = new THREE.MeshStandardMaterial({ color: 0xe9dcc8, roughness: 0.7, metalness: 0.05 })
  const skinDark = new THREE.MeshStandardMaterial({ color: 0xd9c6ad, roughness: 0.82 })

  const isMale = p.gender === '男'
  const bmiN = clamp(p.bmi / 22, 0.82, 1.7)
  const fatT = clamp((p.bodyFat - 12) / 26, 0, 1)
  const waistT = clamp((p.waistCm - 62) / 48, 0, 1)

  const shoulderW = H * (isMale ? 0.26 : 0.215)
  const hipW = H * (isMale ? 0.175 : 0.225)
  const chestHalf = shoulderW * 0.6 * (0.92 + 0.4 * (bmiN - 0.82))
  const waistHalf = H * (0.082 + 0.07 * waistT)
  const bellyHalf = waistHalf * (1 + 0.55 * fatT)

  const legLen = H * 0.44
  const legR = H * (0.055 + 0.02 * waistT)
  const hipY = legLen + H * 0.04
  const shoulderY = H * 0.83
  const chestCY = (shoulderY + hipY) / 2 + H * 0.03

  // 腿 + 足
  for (const s of [-1, 1]) {
    const leg = new THREE.Mesh(new THREE.CapsuleGeometry(legR, legLen - legR * 2, 8, 16), skin)
    leg.position.set(s * hipW * 0.5, legLen / 2, 0)
    leg.castShadow = true
    leg.receiveShadow = true
    g.add(leg)
    const foot = new THREE.Mesh(new THREE.BoxGeometry(legR * 1.8, H * 0.03, legR * 3.0), skinDark)
    foot.position.set(s * hipW * 0.5, H * 0.015, legR * 0.8)
    foot.castShadow = true
    g.add(foot)
  }

  // 骨盆
  const pelvis = new THREE.Mesh(new THREE.SphereGeometry(1, 28, 20), skin)
  pelvis.scale.set(hipW, H * 0.12, hipW * 0.7)
  pelvis.position.y = hipY
  pelvis.castShadow = true
  g.add(pelvis)

  // 腰腹
  const abdomen = new THREE.Mesh(
    new THREE.CapsuleGeometry(waistHalf, chestCY - hipY - waistHalf * 2, 10, 20),
    skin,
  )
  abdomen.position.y = (chestCY + hipY) / 2
  abdomen.castShadow = true
  g.add(abdomen)

  // 胸
  const chest = new THREE.Mesh(new THREE.SphereGeometry(1, 30, 22), skin)
  chest.scale.set(chestHalf, (shoulderY - chestCY) * 0.62 + chestHalf * 0.5, chestHalf * 0.72)
  chest.position.y = (shoulderY + chestCY) / 2 + H * 0.01
  chest.castShadow = true
  g.add(chest)

  // 腹部（向前突出，随体脂增长）
  const belly = new THREE.Mesh(new THREE.SphereGeometry(1, 28, 20), skin)
  belly.scale.set(bellyHalf, bellyHalf * 1.15, bellyHalf * 0.8)
  belly.position.set(0, (chestCY + hipY) / 2 + H * 0.01, bellyHalf * 0.55)
  belly.castShadow = true
  g.add(belly)

  // 肩
  const shoulderBar = new THREE.Mesh(new THREE.CapsuleGeometry(H * 0.05, shoulderW - H * 0.05, 8, 16), skin)
  shoulderBar.rotation.z = Math.PI / 2
  shoulderBar.position.y = shoulderY
  shoulderBar.castShadow = true
  g.add(shoulderBar)

  // 臂
  for (const s of [-1, 1]) {
    const armLen = H * 0.34
    const armR = H * 0.038
    const arm = new THREE.Mesh(new THREE.CapsuleGeometry(armR, armLen - armR * 2, 8, 16), skin)
    arm.position.set(s * (shoulderW * 0.5 + armR * 0.5), shoulderY - armLen / 2 - H * 0.02, 0)
    arm.rotation.z = s * 0.12
    arm.castShadow = true
    g.add(arm)
  }

  // 颈
  const neck = new THREE.Mesh(new THREE.CylinderGeometry(H * 0.045, H * 0.05, H * 0.07, 16), skinDark)
  neck.position.y = shoulderY + H * 0.05
  neck.castShadow = true
  g.add(neck)

  // 头
  const headR = H * 0.105
  const head = new THREE.Mesh(new THREE.SphereGeometry(headR, 32, 24), skin)
  head.position.y = shoulderY + H * 0.05 + headR + H * 0.01
  head.castShadow = true
  g.add(head)

  return g
}

// 半透明体脂层（杏橘色），可视化脂肪分布
function buildFat(p: BodyProfile): THREE.Group {
  const g = new THREE.Group()
  const H = Math.max(1.4, p.heightCm / 100)
  const mat = new THREE.MeshStandardMaterial({
    color: 0xff7a5c, roughness: 0.45, metalness: 0,
    transparent: true, opacity: 0.26, depthWrite: false,
  })
  const fatT = clamp((p.bodyFat - 12) / 26, 0, 1)
  const waistT = clamp((p.waistCm - 62) / 48, 0, 1)
  const bellyHalf = H * (0.082 + 0.07 * waistT) * (1 + 0.55 * fatT) * 1.08
  const hipY = H * 0.44 + H * 0.04
  const chestCY = (H * 0.83 + hipY) / 2 + H * 0.03
  const belly = new THREE.Mesh(new THREE.SphereGeometry(1, 24, 18), mat)
  belly.scale.set(bellyHalf, bellyHalf * 1.15, bellyHalf * 0.85)
  belly.position.set(0, (chestCY + hipY) / 2 + H * 0.01, bellyHalf * 0.55)
  g.add(belly)
  return g
}

function rebuild(): void {
  if (!scene) return
  if (bodyGroup) { scene.remove(bodyGroup); disposeObject(bodyGroup); bodyGroup = null }
  if (fatGroup) { scene.remove(fatGroup); disposeObject(fatGroup); fatGroup = null }
  if (!props.profile) return
  bodyGroup = buildBody(props.profile)
  scene.add(bodyGroup)
  fatGroup = buildFat(props.profile)
  fatGroup.visible = props.showFat
  scene.add(fatGroup)
}

function animate(): void {
  frameId = requestAnimationFrame(animate)
  if (controls) controls.update()
  if (renderer && scene && camera) renderer.render(scene, camera)
}

function resetView(): void {
  if (!camera || !controls) return
  camera.position.set(...CAM_POS)
  controls.target.set(...TARGET)
  controls.update()
}

onMounted(() => {
  const host = canvasHost.value
  if (!host) return
  const w = host.clientWidth || 800
  const h = host.clientHeight || 600

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(40, w / h, 0.1, 100)
  camera.position.set(...CAM_POS)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setSize(w, h)
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  host.appendChild(renderer.domElement)
  renderer.domElement.style.display = 'block'
  renderer.domElement.style.width = '100%'
  renderer.domElement.style.height = '100%'

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 1.8
  controls.maxDistance = 6
  controls.maxPolarAngle = Math.PI * 0.95
  controls.target.set(...TARGET)
  controls.autoRotate = props.autoRotate
  controls.autoRotateSpeed = 1.2

  // 灯光：半球补光 + 主光（投影）+ 杏橘边缘光
  const hemi = new THREE.HemisphereLight(0xffffff, 0xb89b7a, 0.9)
  scene.add(hemi)
  const key = new THREE.DirectionalLight(0xffffff, 1.6)
  key.position.set(2.5, 4, 3)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  key.shadow.camera.near = 0.5
  key.shadow.camera.far = 20
  key.shadow.bias = -0.0005
  scene.add(key)
  const rim = new THREE.DirectionalLight(0xff8a6b, 0.7)
  rim.position.set(-3, 2, -2)
  scene.add(rim)

  // 接触阴影承接面
  const ground = new THREE.Mesh(
    new THREE.CircleGeometry(2.2, 48),
    new THREE.ShadowMaterial({ opacity: 0.22 }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = 0
  ground.receiveShadow = true
  scene.add(ground)

  rebuild()
  animate()

  resizeObserver = new ResizeObserver(() => {
    if (!renderer || !camera || !host) return
    const ww = host.clientWidth
    const hh = host.clientHeight
    if (ww === 0 || hh === 0) return
    camera.aspect = ww / hh
    camera.updateProjectionMatrix()
    renderer.setSize(ww, hh)
  })
  resizeObserver.observe(host)
})

watch(() => props.profile, () => rebuild())
watch(() => props.showFat, (v) => { if (fatGroup) fatGroup.visible = v })
watch(() => props.autoRotate, (v) => { if (controls) controls.autoRotate = v })

onBeforeUnmount(() => {
  cancelAnimationFrame(frameId)
  if (resizeObserver) resizeObserver.disconnect()
  if (controls) controls.dispose()
  if (bodyGroup) disposeObject(bodyGroup)
  if (fatGroup) disposeObject(fatGroup)
  if (renderer) {
    renderer.dispose()
    if (renderer.domElement.parentNode) {
      renderer.domElement.parentNode.removeChild(renderer.domElement)
    }
  }
})

defineExpose({ resetView })
</script>

<template>
  <div ref="canvasHost" class="bm-canvas-host" />
</template>

<style scoped>
.bm-canvas-host {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
</style>
