/** Geometry only: route result ports around card rectangles, never through their titles. */
export function resultRoute(v:{sourceX:number;sourceY:number;targetX:number;targetY:number;sourceHeight:number;targetHeight:number;sourceFraction:number;targetFraction:number;offset:number}){
 const sourceTop=v.sourceY-v.sourceHeight*v.sourceFraction,sourceBottom=sourceTop+v.sourceHeight,targetTop=v.targetY-v.targetHeight*v.targetFraction;
 const gap=targetTop-sourceBottom,railY=gap>35?(sourceBottom+targetTop)/2:Math.min(sourceTop,targetTop)-40;
 const right=v.sourceX+28,left=v.targetX-28;
 return {path:`M ${v.sourceX} ${v.sourceY} L ${right} ${v.sourceY} L ${right} ${railY} L ${left} ${railY} L ${left} ${v.targetY} L ${v.targetX} ${v.targetY}`,labelX:(v.sourceX+v.targetX)/2+v.offset*.75,labelY:railY};
}
