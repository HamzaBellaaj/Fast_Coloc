import { DestroyRef, Directive, ElementRef, NgZone, afterNextRender, inject } from '@angular/core';
@Directive({selector:'[fcReveal]'})
export class Reveal {
  constructor(){
    const element=inject<ElementRef<HTMLElement>>(ElementRef).nativeElement,destroy=inject(DestroyRef),zone=inject(NgZone);
    afterNextRender(()=>zone.runOutsideAngular(()=>{
      const reduce=matchMedia('(prefers-reduced-motion: reduce)');if(reduce.matches)return;
      element.classList.add('reveal-pending');
      const show=()=>{element.classList.remove('reveal-pending');observer.disconnect();};
      const observer=new IntersectionObserver(entries=>{if(entries.some(e=>e.isIntersecting))show();},{threshold:.08});
      observer.observe(element);reduce.addEventListener('change',show);
      destroy.onDestroy(()=>{observer.disconnect();reduce.removeEventListener('change',show);});
    }));
  }
}
