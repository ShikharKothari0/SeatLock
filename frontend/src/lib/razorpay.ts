import type { BookingResponse } from '../types/api';
export interface RazorpayAttempt {
  id:string; status:'CREATING'|'PENDING'|'EXPIRED'|'SUCCEEDED'|'REFUND_PENDING'|'REFUNDED';
  orderId:string|null; keyId:string; amountMinor:number; currency:string; expiresAt:string; booking:BookingResponse|null; testMode:boolean;
}
type CheckoutResult={razorpay_payment_id:string;razorpay_signature:string};
type CheckoutOptions={key:string;order_id:string;name:string;description:string;handler:(result:CheckoutResult)=>void;modal:{ondismiss:()=>void};retry:{enabled:boolean}};
declare global {interface Window {Razorpay?:new(options:CheckoutOptions)=>{open:()=>void;on:(event:string,callback:()=>void)=>void;close:()=>void};}}
let loading:Promise<void>|null=null;
export function loadRazorpay():Promise<void> {
  if(window.Razorpay)return Promise.resolve();
  if(loading)return loading;
  loading=new Promise<void>((resolve,reject)=>{
    const script=document.createElement('script');script.src='https://checkout.razorpay.com/v1/checkout.js';script.async=true;
    const timeout=setTimeout(()=>{script.remove();loading=null;reject(new Error('Checkout took too long to load. Please retry.'));},15000);
    script.onload=()=>{clearTimeout(timeout);if(window.Razorpay)resolve();else{loading=null;reject(new Error('Checkout is unavailable.'));}};
    script.onerror=()=>{clearTimeout(timeout);script.remove();loading=null;reject(new Error('Unable to load Razorpay checkout. Please retry.'));};
    document.head.appendChild(script);
  });return loading;
}
