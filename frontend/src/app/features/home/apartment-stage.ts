import { Component, DestroyRef, ElementRef, NgZone, afterNextRender, inject, signal } from '@angular/core';
@Component({selector:'fc-apartment-stage',template:`<div class="apartment-world" [class.live]="ready()" [attr.data-renderer]="ready() ? 'webgl' : 'static'">
<div class="world-halo" aria-hidden="true"></div><img class="world-poster" src="/assets/apartment-poster.png" width="800" height="574" alt="Appartement miniature illustratif FastColoc : salon partagé et deux espaces personnels. Ce décor ne représente pas une annonce." fetchpriority="high" />
<div class="world-canvas" aria-hidden="true"></div><div class="world-stamp" aria-hidden="true"><img src="/assets/fastcoloc-symbol.svg" width="32" height="32" alt="" /> Une place pour toi.</div><div class="interface-echo" aria-hidden="true"><i></i><i></i><i></i></div></div>
<div class="story-caption"><span>0{{phase()+1}} / 03 · LE PETIT MONDE FASTCOLOC</span><p>{{phase()===0 ? 'Une coloc, mille façons de se sentir chez soi.' : phase()===1 ? 'Plus qu’un logement. Un endroit à partager.' : 'Du petit monde aux vraies rencontres.'}}</p><small>Décor illustratif · les annonces réelles t’attendent juste après.</small></div>`,styleUrl:'./apartment-stage.css'})
export class ApartmentStage {
  ready=signal(false);phase=signal(0);
  private element:HTMLElement=inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private zone=inject(NgZone);private destroy=inject(DestroyRef);
  constructor(){afterNextRender(()=>this.zone.runOutsideAngular(()=>this.start()));}
  private start(){
    const story=this.element.closest<HTMLElement>('.home-story')!;
    const root=story.parentElement!,target=this.element.querySelector<HTMLElement>('.world-canvas')!;
    const reduce=matchMedia('(prefers-reduced-motion: reduce)'),compact=matchMedia('(max-width: 650px)');
    const device=navigator as Navigator & {deviceMemory?:number;connection?:{saveData?:boolean}};
    let disposed=false,visible=true,frame=0,progress=0,loading=false,failed=false;
    let scene: {render:(p:number)=>void;dispose:()=>void}|undefined;
    const light=()=>failed||reduce.matches||compact.matches||device.connection?.saveData||(device.deviceMemory??8)<=2||navigator.hardwareConcurrency<=2;
    const refresh=()=>{if(!disposed&&!frame)frame=requestAnimationFrame(update);};
    const update=()=>{
      frame=0;if(disposed)return;
      const rect=story.getBoundingClientRect(),distance=Math.max(1,rect.height-innerHeight*.75);
      progress=light()?0:Math.max(0,Math.min(1,-rect.top/distance));
      const unfold=Math.min(1,progress/.6),exit=Math.max(0,(progress-.58)/.42);
      story.style.setProperty('--scene-scale',String(1-exit*.16));story.style.setProperty('--scene-opacity',String(1-exit));
      story.style.setProperty('--echo-opacity',String(Math.min(1,exit*1.8)));story.style.setProperty('--echo-y',String(40-exit*40)+'px');
      root.style.setProperty('--story-progress',String(progress));
      const phase=progress<.23?0:progress<.66?1:2;if(this.phase()!==phase)this.zone.run(()=>this.phase.set(phase));
      story.classList.toggle('story-static',!!light());if(scene&&visible&&!document.hidden)scene.render(unfold);
    };
    const initialize=async()=>{
      if(disposed||light()||loading||scene||failed)return;loading=true;
      try{const module=await import('./apartment-scene');if(disposed||light())return;
        scene=module.createApartment(target,()=>{failed=true;scene?.dispose();scene=undefined;this.zone.run(()=>this.ready.set(false));refresh();});
        this.zone.run(()=>this.ready.set(true));refresh();
      }catch{failed=true;this.zone.run(()=>this.ready.set(false));refresh();}finally{loading=false;}
    };
    const preferences=()=>{if(light()&&scene){scene.dispose();scene=undefined;this.zone.run(()=>this.ready.set(false));}refresh();void initialize();};
    const resize=new ResizeObserver(()=>{refresh();void initialize();});resize.observe(this.element);
    const intersection=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;if(visible)refresh();});intersection.observe(this.element);
    window.addEventListener('scroll',refresh,{passive:true});document.addEventListener('visibilitychange',refresh);
    reduce.addEventListener('change',preferences);compact.addEventListener('change',preferences);refresh();void initialize();
    this.destroy.onDestroy(()=>{disposed=true;cancelAnimationFrame(frame);scene?.dispose();resize.disconnect();intersection.disconnect();window.removeEventListener('scroll',refresh);document.removeEventListener('visibilitychange',refresh);reduce.removeEventListener('change',preferences);compact.removeEventListener('change',preferences);});
  }
}
