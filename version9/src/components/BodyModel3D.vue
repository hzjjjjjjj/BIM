<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'

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
let scanRing: THREE.Mesh | null = null
let resizeObserver: ResizeObserver | null = null
let bodyHeight = 1.7
const fatMats: THREE.MeshBasicMaterial[] = []

const CAM_POS: [number, number, number] = [0, 1.05, 3.2]
const TARGET: [number, number, number] = [0, 0.95, 0]
const RIM = new THREE.Color(0x57e8d4) // 青绿扫描辉光

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

// 体脂热力配色：偏瘦(青绿) → 正常(琥珀) → 偏高(杏橙)
function heatColor(bodyFat: number): THREE.Color {
  const t = clamp((bodyFat - 10) / 28, 0, 1)
  const c1 = new THREE.Color(0x2dd4bf)
  const c2 = new THREE.Color(0xf3b24a)
  const c3 = new THREE.Color(0xff6b57)
  return t < 0.5 ? c1.clone().lerp(c2, t / 0.5) : c2.clone().lerp(c3, (t - 0.5) / 0.5)
}

const fresnelVert = `
varying vec3 vN; varying vec3 vV;
void main(){
  vec4 mv = modelViewMatrix * vec4(position,1.0);
  vN = normalize(normalMatrix * normal);
  vV = -mv.xyz;
  gl_Position = projectionMatrix * mv;
}`
const fresnelFrag = `
uniform vec3 glowColor; uniform float power; uniform float strength;
varying vec3 vN; varying vec3 vV;
void main(){
  float f = pow(1.0 - abs(dot(normalize(vN), normalize(vV))), power);
  gl_FragColor = vec4(glowColor, f * strength);
}`
function makeFresnel(): THREE.ShaderMaterial {
  return new THREE.ShaderMaterial({
    uniforms: { glowColor: { value: RIM }, power: { value: 2.6 }, strength: { value: 0.85 } },
    vertexShader: fresnelVert,
    fragmentShader: fresnelFrag,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    side: THREE.FrontSide,
  })
}

// 由健康指标推导的人体孪生（平滑扫描体）
function buildBody(p: BodyProfile): THREE.Group {
  const g = new THREE.Group()
  const H = Math.max(1.5, p.heightCm / 100)
  bodyHeight = H
  const isMale = p.gender === '男'
  const bmiN = clamp(p.bmi / 22, 0.8, 1.8)
  const fatT = clamp((p.bodyFat - 12) / 26, 0, 1)
  const waistT = clamp((p.waistCm - 62) / 48, 0, 1)

  const heat = heatColor(p.bodyFat)
  const skinMat = new THREE.MeshPhysicalMaterial({
    color: heat, roughness: 0.3, metalness: 0.0,
    clearcoat: 0.55, clearcoatRoughness: 0.45,
    transparent: true, opacity: 0.9,
    emissive: heat, emissiveIntensity: 0.14, envMapIntensity: 0.9,
  })
  const fres = makeFresnel()
  const wireMat = new THREE.MeshBasicMaterial({ color: RIM, wireframe: true, transparent: true, opacity: 0.07, depthWrite: false })

  const addPart = (
    geom: THREE.BufferGeometry, mat: THREE.Material,
    pos?: [number, number, number], rot?: [number, number, number], scale?: [number, number, number],
  ) => {
    const m = new THREE.Mesh(geom, mat)
    if (pos) m.position.set(...pos)
    if (rot) m.rotation.set(...rot)
    if (scale) m.scale.set(...scale)
    m.castShadow = true
    g.add(m)
    const rim = new THREE.Mesh(geom, fres)
    if (pos) rim.position.copy(m.position)
    if (rot) rim.rotation.copy(m.rotation)
    if (scale) rim.scale.copy(m.scale)
    g.add(rim)
    return m
  }

  const shoulderW = H * (isMale ? 0.255 : 0.215)
  const hipW = H * (isMale ? 0.17 : 0.22)
  const neckHalf = H * 0.042
  const chestHalf = H * (isMale ? 0.125 : 0.115) * (0.95 + 0.18 * (bmiN - 0.8))
  const waistHalf = H * (0.072 + 0.03 * waistT + 0.028 * fatT)
  const hipHalf = hipW * 0.62

  const hipY = H * 0.46
  const waistY = H * 0.60
  const chestY = H * 0.74
  const shoulderY = H * 0.86
  const neckY = H * 0.905
  const headY = H * 0.96
  const headR = H * 0.1

  // 腿 + 足
  const legLen = hipY
  const legR = H * (0.052 + 0.018 * waistT)
  for (const s of [-1, 1]) {
    addPart(
      new THREE.CapsuleGeometry(legR, legLen - legR * 2, 10, 18), skinMat,
      [s * hipW * 0.5, legLen / 2, 0],
    )
    const foot = new THREE.Mesh(new THREE.SphereGeometry(1, 18, 12), skinMat)
    foot.scale.set(legR * 1.7, H * 0.028, legR * 3.0)
    foot.position.set(s * hipW * 0.5, H * 0.02, legR * 0.9)
    foot.castShadow = true
    g.add(foot)
    const footRim = new THREE.Mesh(foot.geometry, fres)
    footRim.scale.copy(foot.scale); footRim.position.copy(foot.position)
    g.add(footRim)
  }

  // 平滑躯干（LatheGeometry：骨盆→腰→胸→肩→颈，去堆叠球）
  const profile: THREE.Vector2[] = [
    new THREE.Vector2(hipHalf * 0.85, hipY - H * 0.02),
    new THREE.Vector2(hipHalf, hipY),
    new THREE.Vector2(waistHalf, waistY),
    new THREE.Vector2(chestHalf, chestY),
    new THREE.Vector2(shoulderW * 0.5 * 0.9, shoulderY - H * 0.01),
    new THREE.Vector2(neckHalf * 1.25, neckY),
  ]
  const trunk = new THREE.LatheGeometry(profile, 48)
  addPart(trunk, skinMat)
  const trunkWire = new THREE.Mesh(trunk, wireMat)
  g.add(trunkWire)

  // 颈
  addPart(
    new THREE.CylinderGeometry(neckHalf * 0.9, neckHalf * 1.2, headY - neckY + H * 0.01, 20), skinMat,
    [0, (neckY + headY) / 2, 0],
  )
  // 头
  addPart(new THREE.SphereGeometry(headR, 32, 24), skinMat, [0, headY + headR * 0.6, 0])

  // 肩（横向胶囊，衔接手臂）
  addPart(
    new THREE.CapsuleGeometry(H * 0.05, shoulderW - H * 0.05, 10, 18), skinMat,
    [0, shoulderY, 0], [0, 0, Math.PI / 2],
  )

  // 臂（随肩端自然外展）
  const armLen = H * 0.32
  const armR = H * 0.036
  for (const s of [-1, 1]) {
    addPart(
      new THREE.CapsuleGeometry(armR, armLen - armR * 2, 10, 18), skinMat,
      [s * (shoulderW * 0.5 + armR * 0.4), shoulderY - armLen / 2 - H * 0.01, 0],
      [0, 0, s * 0.1],
    )
  }

  return g
}

// 半透明体脂层（杏橙发光，脉冲可视化脂肪分布）
function buildFat(p: BodyProfile): THREE.Group {
  const g = new THREE.Group()
  fatMats.length = 0
  const H = Math.max(1.5, p.heightCm / 100)
  const fatT = clamp((p.bodyFat - 12) / 26, 0, 1)
  const waistT = clamp((p.waistCm - 62) / 48, 0, 1)

  const waistHalf = H * (0.072 + 0.03 * waistT + 0.028 * fatT) * 1.12
  const hipY = H * 0.46
  const chestY = H * 0.74

  const fatMat = new THREE.MeshBasicMaterial({
    color: 0xff7a4d, transparent: true, opacity: 0.22,
    blending: THREE.AdditiveBlending, depthWrite: false,
  })
  fatMats.push(fatMat)

  // 腹部脂肪壳（放大躯干下段的胶囊）
  const belly = new THREE.Mesh(
    new THREE.CapsuleGeometry(waistHalf, chestY - hipY - waistHalf * 2, 12, 24), fatMat,
  )
  belly.position.y = (chestY + hipY) / 2
  belly.scale.set(1.05, 1.0, 0.92)
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
  const t = performance.now()
  if (scanRing && scene) {
    const ph = (t % 4200) / 4200
    scanRing.position.y = bodyHeight * (0.08 + ph * 0.86)
    ;(scanRing.material as THREE.MeshBasicMaterial).opacity = 0.55 * Math.sin(ph * Math.PI)
  }
  if (fatGroup && fatGroup.visible) {
    const pulse = 0.16 + 0.14 * (0.5 + 0.5 * Math.sin(t / 420))
    for (const m of fatMats) m.opacity = pulse
  }
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
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  host.appendChild(renderer.domElement)
  renderer.domElement.style.display = 'block'
  renderer.domElement.style.width = '100%'
  renderer.domElement.style.height = '100%'

  // 柔和环境光照（影棚 IBL），大幅提升材质质感
  const pmrem = new THREE.PMREMGenerator(renderer)
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 1.8
  controls.maxDistance = 6
  controls.maxPolarAngle = Math.PI * 0.95
  controls.target.set(...TARGET)
  controls.autoRotate = props.autoRotate
  controls.autoRotateSpeed = 1.1

  // 灯光：半球补光 + 主光（投影）+ 杏橙边缘光
  const hemi = new THREE.HemisphereLight(0xffffff, 0x9fb8c4, 0.5)
  scene.add(hemi)
  const key = new THREE.DirectionalLight(0xffffff, 1.15)
  key.position.set(2.6, 4, 3)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  key.shadow.camera.near = 0.5
  key.shadow.camera.far = 20
  key.shadow.bias = -0.0005
  scene.add(key)
  const rim = new THREE.DirectionalLight(0xff8a6b, 0.5)
  rim.position.set(-3, 2.2, -2)
  scene.add(rim)

  // 扫描平台 + 发光环
  const platform = new THREE.Mesh(
    new THREE.CircleGeometry(1.9, 64),
    new THREE.MeshStandardMaterial({ color: 0x0c1620, roughness: 0.9, metalness: 0.1, transparent: true, opacity: 0.55 }),
  )
  platform.rotation.x = -Math.PI / 2
  platform.position.y = 0
  platform.receiveShadow = true
  scene.add(platform)
  const ringBase = new THREE.Mesh(
    new THREE.TorusGeometry(1.85, 0.012, 8, 80),
    new THREE.MeshBasicMaterial({ color: RIM, transparent: true, opacity: 0.5, blending: THREE.AdditiveBlending, depthWrite: false }),
  )
  ringBase.rotation.x = -Math.PI / 2
  ringBase.position.y = 0.002
  scene.add(ringBase)

  // 移动扫描环
  scanRing = new THREE.Mesh(
    new THREE.TorusGeometry(0.9, 0.01, 8, 64),
    new THREE.MeshBasicMaterial({ color: RIM, transparent: true, opacity: 0.5, blending: THREE.AdditiveBlending, depthWrite: false }),
  )
  scanRing.rotation.x = Math.PI / 2
  scene.add(scanRing)

  // 接触阴影承接面
  const ground = new THREE.Mesh(
    new THREE.CircleGeometry(2.2, 48),
    new THREE.ShadowMaterial({ opacity: 0.25 }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = 0.001
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
