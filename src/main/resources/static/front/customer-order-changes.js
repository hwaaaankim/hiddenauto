(function () {
 'use strict';
 let dialog;
 document.addEventListener('click',function(event){
  const button=event.target.closest('[data-customer-change-target]');
  if(button){
   event.preventDefault();event.stopPropagation();
   const panel=document.getElementById(button.dataset.customerChangeTarget);if(!panel)return;
   if(!dialog){dialog=document.createElement('dialog');dialog.className='customer-change-dialog';document.body.append(dialog);}
   dialog.replaceChildren();
   const head=document.createElement('header'),title=document.createElement('strong'),close=document.createElement('button');
   title.textContent=panel.dataset.title||'변경 안내';title.id='customer-change-dialog-title';
   close.type='button';close.textContent='닫기';close.addEventListener('click',()=>dialog.close());
   head.append(title,close);const body=document.createElement('div');body.className='customer-change-dialog-body';
   for(const child of panel.children)body.append(child.cloneNode(true));dialog.append(head,body);
   dialog.setAttribute('aria-labelledby',title.id);dialog.showModal();return;
  }
  const jump=event.target.closest('[data-customer-order-jump]');if(!jump)return;
  const target=document.getElementById(jump.getAttribute('href').slice(1));if(!target)return;
  event.preventDefault();event.stopPropagation();if(dialog&&dialog.open)dialog.close();
  const slide=target.closest('.task-list-order-slide');
  if(slide&&!slide.classList.contains('task-list-is-open')){
   const toggle=document.querySelector('[data-target="#'+slide.id+'"]');if(toggle)toggle.click();
  }
  const history=target.querySelector('.customer-product-history');if(history)history.open=true;
  target.scrollIntoView({block:'center',behavior:'smooth'});target.setAttribute('tabindex','-1');target.focus({preventScroll:true});
 });
})();
