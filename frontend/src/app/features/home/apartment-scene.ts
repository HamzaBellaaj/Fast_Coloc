import * as T from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';

/** Procedural illustration only. No catalogue model or animation loop. */
export function createApartment(host: HTMLElement, lost: () => void) {
  const renderer = new T.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' });
  renderer.setPixelRatio(Math.min(devicePixelRatio, innerWidth < 900 ? 1.25 : 1.75));
  renderer.outputColorSpace = T.SRGBColorSpace;
  renderer.toneMapping = T.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.35;
  renderer.shadowMap.enabled = innerWidth >= 900;
  renderer.shadowMap.type = T.PCFSoftShadowMap;
  renderer.domElement.setAttribute('aria-hidden', 'true');
  renderer.domElement.addEventListener('webglcontextlost', contextLost);
  function contextLost(event: Event) { event.preventDefault(); lost(); }
  host.appendChild(renderer.domElement);
  const scene = new T.Scene();
  const camera = new T.OrthographicCamera(-6, 6, 5, -5, .1, 80);
  const world = new T.Group(); scene.add(world);
  const mats: T.Material[] = []; const geometries = new Set<T.BufferGeometry>();
  const material = (color: string, extra: T.MeshStandardMaterialParameters = {}) => {
    const m = new T.MeshStandardMaterial({color, roughness: .82, ...extra}); mats.push(m); return m;
  };
  const cream = material('#FFF6EF'), violet = material('#7138D8'), coral = material('#FF7954');
  const pink = material('#FFB9D6'), plum = material('#251532'), wood = material('#FFB9D6', {roughness:1});
  const wallMaterial = material('#FFF6EF', {transparent:true});
  function mesh(geometry: T.BufferGeometry, mat: T.Material, x:number,y:number,z:number, parent:T.Group=world) {
    geometries.add(geometry); const m = new T.Mesh(geometry, mat); m.position.set(x,y,z);
    m.castShadow=true; m.receiveShadow=true; parent.add(m); return m;
  }
  function box(w:number,h:number,d:number,x:number,y:number,z:number,mat:T.Material,r=.09,parent:T.Group=world) {
    return mesh(new RoundedBoxGeometry(w,h,d,2,Math.min(r,w/3,h/3,d/3)),mat,x,y,z,parent);
  }
  function cylinder(rt:number,rb:number,h:number,x:number,y:number,z:number,mat:T.Material,parent:T.Group=world) {
    return mesh(new T.CylinderGeometry(rt,rb,h,16),mat,x,y,z,parent);
  }
  function leaf(x:number,y:number,z:number,angle:number,parent:T.Group) {
    const m=mesh(new T.SphereGeometry(.30,12,8),violet,x,y,z,parent);m.scale.set(.48,1.3,.5);m.rotation.z=angle; return m;
  }
  function plant(x:number,z:number,size=1) {
    const g=new T.Group();g.position.set(x,.15,z);g.scale.setScalar(size);world.add(g);
    cylinder(.25,.18,.48,0,.24,0,coral,g); cylinder(.04,.04,1,0,.8,0,plum,g);
    leaf(-.17,1.12,0,.7,g);leaf(.2,1.4,0,-.6,g);leaf(-.14,1.65,.04,.4,g);
    return g;
  }
  // Open, rounded dollhouse with two private corners and a shared living space.
  box(9,.38,7,0,-.23,0,violet,.18);box(8.8,.12,6.8,0,.02,0,cream,.05);
  const back=box(8.8,2.8,.18,0,1.44,-3.32,wallMaterial);
  const side=box(.18,2.8,6.8,-4.32,1.44,0,wallMaterial);
  // Two framed windows, with a warm afternoon glow.
  const glow=material('#FFF6EF',{emissive:'#FFB9D6',emissiveIntensity:.3});
  for(const x of [-2.5,1.4]) {
    box(1.45,1.4,.12,x,1.8,-3.17,plum,.04);
    box(1.28,1.24,.08,x,1.8,-3.08,glow,.025);
    box(.045,1.24,.12,x,1.8,-3.01,violet,.01);
    box(1.3,.045,.12,x,1.8,-3.01,violet,.01);
    box(1.7,.09,.38,x,1.11,-2.96,wood,.03);
  }
  // Personal spaces: beds, rounded duvets, pillows and bedside lamps.
  for(const [x,mat] of [[-2.6,pink],[.4,coral]] as const) {
    box(2.15,.28,1.7,x,.29,-1.97,plum,.09);
    box(2.10,.23,1.65,x,.52,-1.97,cream,.11);
    box(1.5,.12,1.65,x+.27,.68,-1.97,mat,.06);
    box(.44,.18,.58,x-.73,.72,-1.97,cream,.09);
    box(.14,.95,1.78,x-1.05,.72,-1.97,violet,.05);
    cylinder(.31,.32,.48,x+1.32,.30,-2.3,wood);
    cylinder(.11,.11,.3,x+1.32,.69,-2.3,plum);
    mesh(new T.ConeGeometry(.27,.35,16),cream,x+1.32,.94,-2.3);
  }
  // Low partitions keep the layout legible from above.
  box(.12,1,2.8,-.95,.56,-1.88,wallMaterial,.04);
  box(5.4,.7,.13,-1.5,.42,-.56,wallMaterial,.04);
  // Shared rug and sofa, with cushions in the official palette.
  const rug=mesh(new T.CylinderGeometry(2.25,2.25,.025,40),pink,-.6,.11,1.25);rug.scale.z=.69;
  box(3.1,.4,1.05,-1.05,.43,1.85,violet,.15);
  box(3.1,.87,.27,-1.05,.82,2.27,violet,.12);
  box(.3,.73,1.1,-2.48,.66,1.85,violet,.12);box(.3,.73,1.1,.38,.66,1.85,violet,.12);
  box(1.2,.16,.83,-1.72,.72,1.77,pink,.075);box(1.2,.16,.83,-.34,.72,1.77,coral,.075);
  for(const x of [-1.95,-.15]){const cushion=box(.48,.46,.17,x,1.08,2.03,cream,.08);cushion.rotation.z=x< -1?-.13:.13;}
  // Coffee table and two cups: simple, readable geometry.
  const table=cylinder(.83,.83,.15,-.65,.64,.48,wood); table.scale.z=.76;
  for(const [x,z] of [[-1.15,.20],[-.2,.65],[-.55,.12]]) cylinder(.055,.065,.48,x,.33,z,plum);
  for(const [x,z,mat] of [[-.95,.5,cream],[-.3,.35,coral]] as const) {
    cylinder(.12,.10,.18,x,.8,z,mat);
    cylinder(.09,.09,.005,x,.897,z,plum);
    const handle=mesh(new T.TorusGeometry(.075,.022,6,12),mat,x+.12,.82,z);handle.rotation.y=Math.PI/2;
  }
  box(.4,.035,.5,-.63,.74,.20,violet,.01);
  // Compact kitchen on the right.
  for(const z of [-2.2,-1.25,-.3]){box(.86,.78,.86,3.43,.48,z,cream,.04);box(.02,.05,.25,2.97,.65,z,plum,.01);}
  box(1,.12,2.9,3.40,.94,-1.25,plum,.04);
  box(.58,.025,.63,3.4,1.02,-1.95,violet,.035);
  for(const z of [-.95,-.45]) {const ring=mesh(new T.TorusGeometry(.16,.025,6,16),coral,3.4,1.04,z);ring.rotation.x=Math.PI/2;}
  box(.9,1.72,.76,3.42,.95,-2.72,pink,.06);box(.025,.4,.045,2.94,1.32,-2.9,plum,.01);
  plant(3.28,2.3,1.15);plant(-3.55,.6,.8);
  // Small wall art uses the existing official symbol, never a replacement mark.
  const textures:T.Texture[]=[];let alive=true;
  const texture=new T.TextureLoader().load('/assets/fastcoloc-symbol.svg',()=>{if(alive)render(progress);});textures.push(texture);texture.colorSpace=T.SRGBColorSpace;
  const logoMat=new T.MeshBasicMaterial({map:texture,transparent:true});mats.push(logoMat);
  mesh(new T.PlaneGeometry(.65,.65),logoMat,-.3,2.02,-3.2);
  scene.add(new T.HemisphereLight('#FFF6EF','#7138D8',2.3));
  const sunlight=new T.DirectionalLight('#FFF6EF',4.2);sunlight.position.set(3,9,5);sunlight.castShadow=renderer.shadowMap.enabled;
  sunlight.shadow.mapSize.set(512,512);sunlight.shadow.camera.left=-6;sunlight.shadow.camera.right=6;sunlight.shadow.camera.top=6;sunlight.shadow.camera.bottom=-6;sunlight.shadow.normalBias=.04;scene.add(sunlight);
  const fill=new T.DirectionalLight('#FFB9D6',1.4);fill.position.set(-5,4,-2);scene.add(fill);
  const ground=mesh(new T.PlaneGeometry(200,200),new T.ShadowMaterial({opacity:.12}),0,-.46,0);mats.push(ground.material);ground.rotation.x=-Math.PI/2;
  let progress=0;
  function render(value:number) {
    if(!alive || host.clientWidth===0)return;
    progress=value;
    const width=host.clientWidth,height=host.clientHeight;
    if(renderer.domElement.width!==Math.round(width*renderer.getPixelRatio()) || renderer.domElement.height!==Math.round(height*renderer.getPixelRatio()))renderer.setSize(width,height,false);
    const aspect=width/Math.max(1,height),span=5.9;
    camera.left=-span*aspect;camera.right=span*aspect;camera.top=span;camera.bottom=-span;
    camera.position.set(10,10.8,12.5);camera.lookAt(0,.25,0);camera.zoom=1+Math.sin(value*Math.PI)*.11;camera.updateProjectionMatrix();
    world.rotation.y=-.18+value*.36;world.position.y=Math.sin(value*Math.PI)*.12;
    back.position.z=-3.32-value*.65;side.position.x=-4.32-value*.5;
    wallMaterial.opacity=1-Math.min(1,value*2)*.35;
    renderer.render(scene,camera);
  }
  render(0);
  return {render,dispose(){alive=false;renderer.domElement.removeEventListener('webglcontextlost',contextLost);for(const g of geometries)g.dispose();for(const m of mats)m.dispose();for(const t of textures)t.dispose();renderer.dispose();renderer.forceContextLoss();renderer.domElement.remove();}};
}
