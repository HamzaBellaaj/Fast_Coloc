import { Component, DestroyRef, Directive, ElementRef, Injectable, afterNextRender, inject } from '@angular/core';
import { NavigationCancel, NavigationError, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
@Injectable({providedIn:'root'})
export class LiquidPop {
  private surface?:HTMLElement;private animation?:Animation;
  private destroy=inject(DestroyRef);
  constructor(){inject(Router).events.pipe(takeUntilDestroyed()).subscribe(e=>{if(e instanceof NavigationCancel||e instanceof NavigationError)this.clear();});this.destroy.onDestroy(()=>this.clear());}
  register(element:HTMLElement){this.surface=element;return()=>{this.clear();this.surface=undefined;};}
  play(event:MouseEvent){
    if(event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey||matchMedia('(prefers-reduced-motion: reduce)').matches||!this.surface||this.animation)return;
    const el=this.surface,radius=Math.hypot(innerWidth,innerHeight)*1.15;
    el.style.width=el.style.height=radius*2+'px';el.style.left=event.clientX-radius+'px';el.style.top=event.clientY-radius+'px';el.style.visibility='visible';
    const animation=el.animate([{transform:'scale(.001) rotate(-18deg)',borderRadius:'38% 62% 54% 46% / 44% 36% 64% 56%'},{transform:'scale(1) rotate(8deg)',borderRadius:'49% 51% 48% 52%',offset:.43},{transform:'scale(1) rotate(8deg)',offset:.49},{transform:'translate(25vw,-25vh) scale(.001) rotate(28deg)',borderRadius:'55% 45% 38% 62%'}],{duration:460,easing:'cubic-bezier(.2,.65,.25,1)',fill:'both'});
    this.animation=animation;animation.onfinish=()=>this.clear();animation.oncancel=()=>{el.style.visibility='hidden';};
  }
  private clear(){const a=this.animation;this.animation=undefined;a?.cancel();if(this.surface)this.surface.style.visibility='hidden';}
}
@Directive({selector:'[fcLiquidPop]',host:{'(click)':'pop.play($event)'}})
export class LiquidPopLink {pop=inject(LiquidPop);}
@Component({selector:'fc-liquid-surface',template:'<div class="liquid-blob"></div>',styles:[':host{position:fixed;inset:0;overflow:hidden;z-index:1000;pointer-events:none;contain:strict}.liquid-blob{position:absolute;background:#7138D8;visibility:hidden;will-change:transform;border-radius:45%}@media(prefers-reduced-motion:reduce){:host{display:none}}']})
export class LiquidSurface {
  constructor(){const pop=inject(LiquidPop),element=inject(ElementRef<HTMLElement>),destroy=inject(DestroyRef);afterNextRender(()=>destroy.onDestroy(pop.register(element.nativeElement.querySelector('.liquid-blob')!)));}
}
