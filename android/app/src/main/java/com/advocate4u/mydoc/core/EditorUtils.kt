package com.advocate4u.mydoc.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/** Spreadsheet-safe helpers shared by Word and spreadsheet features. */
object EditorUtils {
    fun replace(text: String, query: String, replacement: String, replaceAll: Boolean): Pair<String, Int> {
        if (query.isEmpty()) return text to 0
        if (replaceAll) { var count=0; var at=0; while (true) { val i=text.indexOf(query,at); if(i<0) break; count++; at=i+max(1,query.length) }; return text.replace(query,replacement) to count }
        val i=text.indexOf(query); return if(i<0) text to 0 else text.substring(0,i)+replacement+text.substring(i+query.length) to 1
    }

    /** Safe spreadsheet subset: arithmetic, references, ranges, IF/AND/OR, SUM/AVERAGE/MIN/MAX/COUNT/COUNTIF/SUMIF, ABS and ROUND. */
    fun evaluateSimpleFormula(value:String,cells:List<List<String>>):String? {
        if(!value.startsWith("=")) return null
        val expr=value.drop(1).trim(); if(expr.isEmpty()) return null
        val countIf=Regex("(?i)^COUNTIF\\(([^,]+),(.+)\\)$").matchEntire(expr)
        if(countIf!=null){ val range=expandRawReference(countIf.groupValues[1].trim(),cells)?:return null; val criterion=countIf.groupValues[2].trim().trim('"'); return range.count{matchesCriterion(it,criterion)}.toString() }
        val sumIf=Regex("(?i)^SUMIF\\(([^,]+),([^,]+)(?:,(.+))?\\)$").matchEntire(expr)
        if(sumIf!=null){ val test=expandRawReference(sumIf.groupValues[1].trim(),cells)?:return null; val criterion=sumIf.groupValues[2].trim().trim('"'); val sumRange=if(sumIf.groupValues[3].isNullOrBlank())test else expandRawReference(sumIf.groupValues[3].trim(),cells)?:return null; return test.indices.sumOf{if(matchesCriterion(test[it],criterion))sumRange.getOrNull(it)?.toDoubleOrNull()?:0.0 else 0.0}.format() }
        val scalar=Regex("(?i)^(ABS)\\(([^()]*)\\)$").matchEntire(expr); if(scalar!=null){val n=operandValue(scalar.groupValues[2].trim(),cells)?:return null;return abs(n).format()}
        val rm=Regex("(?i)^ROUND\\(([^()]*)\\)$").matchEntire(expr); if(rm!=null){val a=rm.groupValues[1].split(',').map{it.trim()}.filter{it.isNotEmpty()};if(a.size !in 1..2)return null;val n=operandValue(a[0],cells)?:return null;val d=if(a.size==2)a[1].toIntOrNull()?:return null else 0;if(d !in 0..6)return null;val f=10.0.pow(d);return(round(n*f)/f).format()}
        val logic=Regex("(?i)^(AND|OR)\\((.*)\\)$").matchEntire(expr); if(logic!=null){val args=splitArgs(logic.groupValues[2]);if(args.isEmpty())return null;val vals=args.map{truthValue(it,cells)?:return null};return(if(logic.groupValues[1].uppercase()=="AND")vals.all{it}else vals.any{it}).toString()}
        val fn=Regex("(?i)^(SUM|AVERAGE|MIN|MAX|COUNT)\\(([^()]*)\\)$").matchEntire(expr); if(fn!=null){val args=splitArgs(fn.groupValues[2]);if(args.isEmpty())return "0";val values=mutableListOf<Double>();for(arg in args){val e=expandReference(arg,cells);if(e!=null)values.addAll(e)else values.add(arg.toDoubleOrNull()?:return null)};return when(fn.groupValues[1].uppercase()){"SUM"->values.sum().format();"AVERAGE"->if(values.isEmpty())"0"else(values.sum()/values.size).format();"MIN"->values.minOrNull()?.format()? : "0";"MAX"->values.maxOrNull()?.format()? : "0";"COUNT"->values.size.toString();else->null}}
        val ifm=Regex("(?i)^IF\\((.*)\\)$").matchEntire(expr); if(ifm!=null){val a=splitArgs(ifm.groupValues[1]);if(a.size!=3)return null;val ok=truthValue(a[0],cells)?:return null;return a[if(ok)1 else 2].trim().trim('"')}
        return evaluateArithmetic(expr,cells)?.format()
    }

    private fun splitArgs(s:String):List<String>{val out=mutableListOf<String>();var depth=0;var start=0;for(i in s.indices){when(s[i]){'('->depth++;')'->depth--;','->if(depth==0){out+=s.substring(start,i).trim();start=i+1}}};out+=s.substring(start).trim();return out.filter{it.isNotEmpty()}}
    private fun matchesCriterion(raw:String,criterion:String):Boolean{val m=Regex("^(>=|<=|<>|=|>|<)(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))$").matchEntire(criterion);if(m!=null){val n=raw.toDoubleOrNull()?:return false;val t=m.groupValues[2].toDoubleOrNull()?:return false;return when(m.groupValues[1]){">"->n>t;"<"->n<t;">="->n>=t;"<="->n<=t;"="->n==t;"<>"->n!=t;else->false}};return raw==criterion}
    private fun truthValue(v:String,c:List<List<String>>):Boolean?{val t=v.trim();if(t.equals("TRUE",true))return true;if(t.equals("FALSE",true))return false;val comparison=Regex("^(.+?)(>=|<=|<>|=|>|<)(.+)$").matchEntire(t)?:return operandValue(t,c)?.let{it!=0.0};val l=operandValue(comparison.groupValues[1].trim(),c)?:return null;val r=operandValue(comparison.groupValues[3].trim(),c)?:return null;return when(comparison.groupValues[2]){">"->l>r;"<"->l<r;">="->l>=r;"<="->l<=r;"="->l==r;"<>"->l!=r;else->null}}
    private fun operandValue(v:String,c:List<List<String>>)=v.trim().toDoubleOrNull()?:expandReference(v.trim(),c)?.singleOrNull()
    private fun evaluateArithmetic(expr:String,cells:List<List<String>>):Double?{val n=expr.replace(" ","");val ms=Regex("(?i)([A-Z]+\\d+|(?:\\d+(?:\\.\\d*)?|\\.\\d+))|([+*/-])").findAll(n).toList();if(ms.isEmpty())return null;var consumed=0;val vals=mutableListOf<Double>();val ops=mutableListOf<Char>();for(m in ms){if(m.range.first!=consumed)return null;val o=m.groupValues[1];if(o.isNotEmpty())vals.add(if(Regex("(?i)^[A-Z]+\\d+$").matches(o))expandReference(o,cells)?.singleOrNull()?:return null else o.toDouble());else ops.add(m.groupValues[2][0]);consumed=m.range.last+1};if(consumed!=n.length||vals.size!=ops.size+1)return null;val rv=mutableListOf(vals.first());val ro=mutableListOf<Char>();for(i in ops.indices){val op=ops[i];val next=vals[i+1];if(op=='*'||op=='/'){val cur=rv.removeLast();rv.add(if(op=='*')cur*next else if(next==0.0)return Double.NaN else cur/next)}else{ro.add(op);rv.add(next)}};var result=rv.first();for(i in ro.indices)result=if(ro[i]=='+')result+rv[i+1]else result-rv[i+1];return result}
    private fun expandReference(r:String,c:List<List<String>>):List<Double>?=expandRawReference(r,c)?.mapNotNull{it.toDoubleOrNull()}
    private fun expandRawReference(r:String,c:List<List<String>>):List<String>?{val s=Regex("(?i)^([A-Z]+)(\\d+)$").matchEntire(r);if(s!=null){val col=column(s.groupValues[1]);val row=s.groupValues[2].toIntOrNull()?.minus(1)?:return null;return if(row>=0&&col>=0)listOf(c.getOrNull(row)?.getOrNull(col)?:"")else null};val m=Regex("(?i)^([A-Z]+)(\\d+):([A-Z]+)(\\d+)$").matchEntire(r)?:return null;val c1=column(m.groupValues[1]);val r1=m.groupValues[2].toInt()-1;val c2=column(m.groupValues[3]);val r2=m.groupValues[4].toInt()-1;if(minOf(r1,r2)<0||minOf(c1,c2)<0)return null;return buildList{for(row in minOf(r1,r2)..maxOf(r1,r2))for(col in minOf(c1,c2)..maxOf(c1,c2))add(c.getOrNull(row)?.getOrNull(col)?:"")}}
    private fun column(v:String)=v.uppercase().fold(0){a,ch->a*26+ch.code-'A'.code+1}-1
    private fun Double.format()=if(isNaN()||isInfinite())toString()else if(this%1.0==0.0)toLong().toString()else toString()
}
