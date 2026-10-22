import {useState,useEffect,useRef,useCallback} from 'react';
import {Link,Navigate,useNavigate} from 'react-router-dom';
import {useQueryClient} from '@tanstack/react-query';
import api from '../../lib/api';
import {loadRazorpay,type RazorpayAttempt} from '../../lib/razorpay';
import {useSessionStore} from '../../store/sessionStore';
import {PriceBreakdown} from '../../components/customer/PriceBreakdown';

export function RazorpayPaymentPage(){
  const session=useSessionStore(),navigate=useNavigate(),queries=useQueryClient();
  const [attempt,setAttempt]=useState<RazorpayAttempt|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[checkoutOpen,setCheckoutOpen]=useState(false);
  const running=useRef(false), mounted=useRef(true);
  const checkoutRef=useRef<{close:()=>void}|null>(null);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;checkoutRef.current?.close();};},[]);
  const accept=useCallback((result:RazorpayAttempt)=>{
    if(!mounted.current)return;
    setAttempt(result);
    if(result.status==='SUCCEEDED' && result.booking){
      useSessionStore.getState().setBookingConfirmed(result.booking);
      void queries.invalidateQueries({queryKey:['bookings']});void queries.invalidateQueries({queryKey:['seats']});
      navigate(`/booking-confirmed/${result.booking.id}`,{replace:true});
    }
  },[navigate,queries]);
  const check=useCallback(async(open=false)=>{
    if(running.current)return;running.current=true;setBusy(true);setError('');
    let starting=true;
    try{
      const state=useSessionStore.getState();
      const request=state.beginConfirmation();
      if(request.provider && request.provider!=='RAZORPAY')throw new Error('Finish your existing demo payment first.');
      useSessionStore.setState({pendingConfirmation:{...request,provider:'RAZORPAY'}});
      let result=(await api.post<RazorpayAttempt>('/payments/razorpay',{quoteId:request.quoteId,seatIds:request.seatIds,idempotencyKey:request.idempotencyKey})).data;
      starting=false;
      result=(await api.post<RazorpayAttempt>(`/payments/razorpay/${result.id}/reconcile`)).data;
      accept(result);
      if(open && result.status==='PENDING' && result.orderId && Date.parse(result.expiresAt)>Date.now()){
        await loadRazorpay();if(!mounted.current)return;
        const saved=result;
        const checkout=new window.Razorpay!({key:saved.keyId,order_id:saved.orderId!,name:'SeatLock',description:'Test booking — no real money',retry:{enabled:true},
          handler:response=>{
            setCheckoutOpen(false);
            void api.post<RazorpayAttempt>(`/payments/razorpay/${saved.id}/verify`,{paymentId:response.razorpay_payment_id,signature:response.razorpay_signature})
              .then(r=>accept(r.data)).catch(()=>setError('We could not verify the payment yet. Check its status before trying again.'));
          },modal:{ondismiss:()=>{if(mounted.current){setCheckoutOpen(false);setError('Checkout closed. Check payment status or reopen checkout to continue.');}}}});
        checkout.on('payment.failed',()=>setError('The test payment was declined. You can retry in checkout.'));
        checkoutRef.current=checkout;setCheckoutOpen(true);checkout.open();
      }
    }catch(err){
      if(starting && [400,404,409].includes((err as {status?:number}).status ?? 0))useSessionStore.getState().rejectConfirmation();
      if(mounted.current)setError((err as Error).message);
    }
    finally{running.current=false;if(mounted.current)setBusy(false);}
  },[accept]);
  useEffect(()=>{
    if(!session.pendingConfirmation || checkoutOpen || attempt?.status==='REFUNDED' || attempt?.status==='EXPIRED')return;
    const timer=setTimeout(()=>void check(),5000);return()=>clearTimeout(timer);
  },[session.pendingConfirmation,checkoutOpen,attempt,check,busy]);
  if(!session.pendingConfirmation && !session.quote && !attempt && !error)return <Navigate to="/checkout" replace/>;
  return <main className="max-w-3xl mx-auto p-6 space-y-5 text-slate-900 dark:text-white">
    <h1 className="text-3xl font-bold">Secure test checkout</h1>
    <p>Razorpay Test Mode. No real money will be charged. Use Razorpay test payment details only.</p>
    {session.quote && <PriceBreakdown quote={session.quote}/>}
    {session.quote && <p className="text-sm">Complete checkout before {new Date(session.quote.expiresAt).toLocaleTimeString()}. A successful test payment received after your reservation expires will be refunded if the booking cannot be confirmed.</p>}
    {attempt && <p role="status">{({CREATING:'Checking whether your payment order was created. Please do not start another payment.',PENDING:'Your payment is awaiting confirmation. You can safely refresh this page.',EXPIRED:'Your reservation has expired. Any late successful test payment will be reconciled and refunded.',SUCCEEDED:'Opening your receipt…',REFUND_PENDING:'The seats could not be confirmed. Your test refund is being processed.',REFUNDED:'Your test payment has been refunded. No booking was created.'})[attempt.status]}</p>}
    {error && <p role="alert" className="text-red-600 dark:text-red-400">{error}</p>}
    {(!attempt || attempt.status==='PENDING') && <button disabled={busy||checkoutOpen} onClick={()=>void check(true)} className="rounded bg-indigo-600 text-white px-4 py-3 disabled:opacity-50">{busy?'Preparing checkout…':attempt?'Open Razorpay checkout':'Pay with Razorpay (test)'}</button>}
    {session.pendingConfirmation && <button disabled={busy||checkoutOpen} onClick={()=>void check()} className="block underline">Check payment status</button>}
    {attempt && ['EXPIRED','REFUNDED'].includes(attempt.status) && <button onClick={()=>{session.rejectConfirmation();session.clearHold();navigate('/');}} className="block underline">Browse events</button>}
    {!session.pendingConfirmation && <Link to="/checkout" className="block underline">Back to checkout</Link>}
  </main>;
}
