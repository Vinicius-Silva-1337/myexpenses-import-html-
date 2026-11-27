package br.com.feira.nfce.financeiro.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.financeiro.data.*
import br.com.feira.nfce.financeiro.domain.Money
import java.time.LocalDate
import java.time.YearMonth
import br.com.feira.nfce.ui.ExpensePalette
import br.com.feira.nfce.ui.MyExpensesFilterChip

@Composable
fun DashboardScreen(vm:FinanceViewModel,modifier:Modifier=Modifier,onAccounts:()->Unit={},onReport:()->Unit={},onTransactions:()->Unit={},onAddTransaction:(String)->Unit={}) {
    val summary by vm.summary.collectAsState();val tx by vm.transactions.collectAsState();val fixed by vm.fixed.collectAsState();val accounts by vm.accounts.collectAsState();val balances by vm.accountBalances.collectAsState()
    var month by remember{mutableStateOf(YearMonth.now())}
    LaunchedEffect(Unit){vm.refresh(month)}
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),contentPadding=PaddingValues(top=4.dp,bottom=96.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Row(Modifier.fillMaxWidth().padding(bottom=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){IconButton(onClick={month=month.minusMonths(1);vm.refresh(month)}){Icon(Icons.Filled.ChevronLeft,"Mês anterior",tint=ExpensePalette.Muted)};Text(monthName(month),Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Ink);IconButton(onClick={if(month<YearMonth.now()){month=month.plusMonths(1);vm.refresh(month)}}){Icon(Icons.Filled.ChevronRight,"Próximo mês",tint=if(month<YearMonth.now())ExpensePalette.Muted else ExpensePalette.Border)}} }
            item { SurfaceCard { Column(Modifier.fillMaxWidth().padding(6.dp)) {
                Text("Saldo atual em contas",color=ExpensePalette.Muted,style=MaterialTheme.typography.bodyMedium)
                Text(Money.format(summary.accountBalanceCents),Modifier.padding(top=4.dp,bottom=16.dp),style=MaterialTheme.typography.headlineMedium,color=if(summary.accountBalanceCents<0)ExpensePalette.Red else ExpensePalette.Ink,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                HorizontalDivider(color=ExpensePalette.Border)
                Text("MOVIMENTAÇÃO DE ${monthName(month).uppercase()}",Modifier.padding(top=14.dp,bottom=8.dp),color=ExpensePalette.Muted,style=MaterialTheme.typography.labelSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,letterSpacing=1.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("↑ Entradas",color=ExpensePalette.Muted,style=MaterialTheme.typography.bodyMedium);Text(Money.format(summary.incomeCents),color=ExpensePalette.Green,style=MaterialTheme.typography.titleSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)}
                Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("↓ Saídas",color=ExpensePalette.Muted,style=MaterialTheme.typography.bodyMedium);Text(Money.format(summary.expenseCents),color=ExpensePalette.Red,style=MaterialTheme.typography.titleSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)}
            } } }
            item { SectionHeading("Contas","Gerenciar",onAccounts) }
            item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                accounts.forEachIndexed { index,account ->
                    val avatarColors=listOf(ExpensePalette.Purple,ExpensePalette.Cyan,ExpensePalette.Green,ExpensePalette.Magenta)
                    Card(Modifier.width(230.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,ExpensePalette.Border),elevation=CardDefaults.cardElevation(0.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).background(avatarColors[index%avatarColors.size],RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Filled.AccountBalanceWallet,null,tint=Color.White,modifier=Modifier.size(20.dp))};Column(Modifier.padding(start=10.dp)){Text(account.name,style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink,maxLines=1);Text(account.type.replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted)}}
                            Column { Text("Saldo disponível",style=MaterialTheme.typography.labelSmall,color=ExpensePalette.Muted);Text(Money.format(balances[account.id]?:account.openingBalanceCents),style=MaterialTheme.typography.titleLarge,color=if((balances[account.id]?:account.openingBalanceCents)<0)ExpensePalette.Red else ExpensePalette.Ink,maxLines=1) }
                        }
                    }
                }
                Card(onClick=onAccounts,modifier=Modifier.width(120.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFCDBCFF)),elevation=CardDefaults.cardElevation(0.dp)){Column(Modifier.fillMaxWidth().height(130.dp),verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(40.dp).background(ExpensePalette.PurpleLight,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Filled.Add,null,tint=ExpensePalette.Purple)};Text("Adicionar",color=ExpensePalette.Purple,style=MaterialTheme.typography.titleSmall)}}
            } }
            val due=pendingFixed(fixed,tx,month)
            if(due.isNotEmpty()) item { SectionHeading("Próximos vencimentos","Ver todas",onAccounts) }
            if(due.isNotEmpty()) item { SurfaceCard { Column(verticalArrangement=Arrangement.spacedBy(2.dp)){due.forEach { item->Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(item.description,style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink);Text("Dia ${item.dueDay}",style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted)};Text(Money.format(item.amountCents),color=ExpensePalette.Red,style=MaterialTheme.typography.titleSmall)}}} } }
            item { SectionHeading("Gastos do mês","Relatório",onReport) }
            item { SurfaceCard { CategoryDonut(summary.categoryExpenses,summary.expenseCents,"total") } }
            item { SectionHeading("Últimas transações","Ver todas",onTransactions) }
            val recent=tx.filter{it.date.startsWith(month.toString())}.take(6)
            if(recent.isEmpty()) item { SurfaceCard { EmptyState("Nenhuma transação em ${monthName(month)}","Toque em + para registrar a primeira.") } }
            items(recent,key={"dash-${it.id}"}){item->TransactionCard(item,onClick=onTransactions)}
        }
        var showAddMenu by remember{mutableStateOf(false)}
        Box(Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=4.dp)){
            DropdownMenu(expanded=showAddMenu,onDismissRequest={showAddMenu=false}){
                DropdownMenuItem(text={Text("Receita")},leadingIcon={Icon(Icons.Filled.ArrowUpward,null,tint=ExpensePalette.Green)},onClick={showAddMenu=false;onAddTransaction("receita")})
                DropdownMenuItem(text={Text("Despesa")},leadingIcon={Icon(Icons.Filled.ArrowDownward,null,tint=ExpensePalette.Red)},onClick={showAddMenu=false;onAddTransaction("despesa")})
                DropdownMenuItem(text={Text("Transferência")},leadingIcon={Icon(Icons.Filled.SwapHoriz,null,tint=ExpensePalette.Purple)},onClick={showAddMenu=false;onAddTransaction("transferencia")})
            }
            SmallFloatingActionButton(onClick={showAddMenu=true},modifier=Modifier.size(48.dp),containerColor=ExpensePalette.Purple,contentColor=Color.White,shape=CircleShape){Icon(Icons.Filled.Add,"Adicionar",modifier=Modifier.size(22.dp))}
        }
    }
}

private fun monthName(month:YearMonth)=month.month.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale("pt","BR")).replaceFirstChar{it.uppercase()}+" ${month.year}"
private fun pendingFixed(fixed:List<FixedExpenseEntity>,tx:List<TransactionEntity>,month:YearMonth)=fixed.filter{item->tx.none{it.origin=="fixa:${item.id}:$month"}}.sortedBy{it.dueDay}.take(3)

@Composable private fun SectionHeading(title:String,action:String?=null,onClick:()->Unit={}){Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(title,style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Ink);if(action!=null)TextButton(onClick=onClick,contentPadding=PaddingValues(horizontal=8.dp)){Text(action,color=ExpensePalette.Purple,style=MaterialTheme.typography.labelLarge)}}}
@Composable private fun SurfaceCard(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,ExpensePalette.Border)){Column(Modifier.fillMaxWidth().padding(16.dp),content=content)}}
@Composable private fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(vertical=26.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink);Text(subtitle,Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted)}}
@Composable private fun CategoryDonut(categories:Map<String,Long>,total:Long,centerCaption:String){val entries=categories.entries.sortedByDescending{it.value}.take(6);if(entries.isEmpty()||total<=0)EmptyState("Nenhuma despesa registrada.","Toque em + para adicionar um gasto.") else Row(Modifier.fillMaxWidth().padding(4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){Box(Modifier.size(130.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){var start=-90f;val denom=total.coerceAtLeast(1L).toFloat();entries.forEachIndexed{index,e->val sweep=e.value.toFloat()/denom*360f;drawArc(ExpensePalette.Chart[index%ExpensePalette.Chart.size],start,sweep,false,style=Stroke(width=size.minDimension*.19f,cap=StrokeCap.Butt));start+=sweep}};Column(horizontalAlignment=Alignment.CenterHorizontally){Text(Money.format(total),style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Ink);Text(centerCaption,style=MaterialTheme.typography.labelSmall,color=ExpensePalette.Muted)}};Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){entries.forEachIndexed{index,e->Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).background(ExpensePalette.Chart[index%ExpensePalette.Chart.size],CircleShape));Column(Modifier.padding(start=8.dp).weight(1f)){Text(e.key,maxLines=1,style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Ink);Text(Money.format(e.value),style=MaterialTheme.typography.labelSmall,color=ExpensePalette.Muted)}}}}} }

@Composable private fun TransactionCard(item:TransactionEntity,onClick:()->Unit){val income=item.type=="receita";Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,ExpensePalette.Border)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if(income)ExpensePalette.GreenLight else ExpensePalette.RedLight),contentAlignment=Alignment.Center){Icon(if(income)Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,null,tint=if(income)ExpensePalette.Green else ExpensePalette.Red,modifier=Modifier.size(18.dp))};Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(item.description,style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink,maxLines=1);Text("${item.categoryName} · ${item.accountName.orEmpty()} · ${item.date}",style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted,maxLines=1)};Text((if(income)"+ " else "− ")+Money.format(item.amountCents),color=if(income)ExpensePalette.Green else ExpensePalette.Red,style=MaterialTheme.typography.labelLarge)}}}

@Composable
fun TransactionsScreen(vm:FinanceViewModel,modifier:Modifier=Modifier,initialAction:String?=null,onInitialActionHandled:()->Unit={}) {
    val tx by vm.transactions.collectAsState(); val transfers by vm.transfers.collectAsState(); val accounts by vm.accounts.collectAsState(); val categories by vm.categories.collectAsState(); val subs by vm.subcategories.collectAsState()
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }; var newType by remember { mutableStateOf<String?>(null) }; var transferOpen by remember { mutableStateOf(false) };var editingTransfer by remember{mutableStateOf<TransferEntity?>(null)};var filter by remember{mutableStateOf("todos")};var deleteTx by remember{mutableStateOf<TransactionEntity?>(null)};var deleteTransfer by remember{mutableStateOf<TransferEntity?>(null)}
    LaunchedEffect(initialAction){if(initialAction!=null){if(initialAction=="transferencia")transferOpen=true else newType=initialAction;onInitialActionHandled()}}
    deleteTx?.let{item->AlertDialog(onDismissRequest={deleteTx=null},title={Text("Excluir transação?")},text={Text("${item.description} · ${Money.format(item.amountCents)}")},confirmButton={TextButton(onClick={vm.deleteTransaction(item);deleteTx=null}){Text("Excluir")}},dismissButton={TextButton(onClick={deleteTx=null}){Text("Cancelar")}})}
    deleteTransfer?.let{item->AlertDialog(onDismissRequest={deleteTransfer=null},title={Text("Excluir transferência?")},confirmButton={TextButton(onClick={vm.deleteTransfer(item);deleteTransfer=null}){Text("Excluir")}},dismissButton={TextButton(onClick={deleteTransfer=null}){Text("Cancelar")}})}
    Column(modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Movimentações",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted,modifier=Modifier.padding(top=8.dp))
        Text("Todas as transações",style=MaterialTheme.typography.headlineSmall,color=ExpensePalette.Ink,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Toque em um lançamento para editar ou excluir",style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={newType="despesa"},modifier=Modifier.weight(1f)){Icon(Icons.Filled.Add,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("Adicionar")};OutlinedButton(onClick={transferOpen=true},modifier=Modifier.weight(1f)){Icon(Icons.Filled.SwapHoriz,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("Transferir")}}
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("todos" to "Todas","receita" to "Receitas","despesa" to "Despesas","transferencia" to "Transferências").forEach{(key,label)->MyExpensesFilterChip(filter==key,{filter=key},label={Text(label)})}}
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(filter in setOf("todos","receita","despesa")) items(tx.filter{filter=="todos"||it.type==filter},key={"t${it.id}"}) { item->
                SurfaceCard{Column(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){val income=item.type=="receita";Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if(income)ExpensePalette.GreenLight else ExpensePalette.RedLight),contentAlignment=Alignment.Center){Icon(if(income)Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,null,tint=if(income)ExpensePalette.Green else ExpensePalette.Red,modifier=Modifier.size(18.dp))};Column(Modifier.weight(1f).padding(start=12.dp)){Text(item.description,style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink,maxLines=1);Text("${item.categoryName} · ${item.accountName.orEmpty()} · ${item.date}",style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted,maxLines=1)};Text((if(income)"+ " else "− ")+Money.format(item.amountCents),color=if(income)ExpensePalette.Green else ExpensePalette.Red,style=MaterialTheme.typography.labelLarge)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={editing=item}){Icon(Icons.Filled.Edit,null,modifier=Modifier.size(16.dp));Text("Editar")};TextButton(onClick={deleteTx=item}){Icon(Icons.Filled.DeleteOutline,null,modifier=Modifier.size(17.dp));Text("Excluir")}}}}
            }
            if(filter in setOf("todos","transferencia")) items(transfers,key={"x${it.id}"}) { item->
                SurfaceCard{Column(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ExpensePalette.PurpleLight),contentAlignment=Alignment.Center){Icon(Icons.Filled.SwapHoriz,null,tint=ExpensePalette.Purple)};Column(Modifier.weight(1f).padding(start=12.dp)){Text("Transferência",style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink);Text("${item.legacySourceName?:accounts.firstOrNull {a->a.id==item.sourceAccountId}?.name} → ${item.legacyDestinationName?:accounts.firstOrNull {a->a.id==item.destinationAccountId}?.name}",style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted,maxLines=1);Text("${item.date} · ${Money.format(item.amountCents)}",style=MaterialTheme.typography.labelLarge,color=ExpensePalette.Purple)} };Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={editingTransfer=item}){Icon(Icons.Filled.Edit,null,modifier=Modifier.size(16.dp));Text("Editar")};TextButton(onClick={deleteTransfer=item}){Icon(Icons.Filled.DeleteOutline,null,modifier=Modifier.size(17.dp));Text("Excluir")}}}}
            }
            if(tx.isEmpty()&&transfers.isEmpty()) item{SurfaceCard{EmptyState("Nada por aqui nesse filtro","Toque em Adicionar para registrar uma movimentação.")}}
        }
    }
    (editing?.let { it to "" } ?: newType?.let { null to it })?.let { pair -> TransactionDialog(pair.first,pair.second,accounts,categories,subs,{editing=null;newType=null},{type,desc,amount,cat,sub,account,date,note-> if(pair.first==null)vm.createTransaction(type,desc,amount,cat,sub,account,date,note) else vm.updateTransaction(pair.first!!,type,desc,amount,cat,sub,account,date,note)}) }
    (editingTransfer?.let{it}?:if(transferOpen)TransferEntity(userId=0,sourceAccountId=0,destinationAccountId=0,amountCents=0,date=LocalDate.now().toString()) else null)?.let{existing->TransferDialog(accounts,existing.takeIf{it.id>0},{transferOpen=false;editingTransfer=null}){from,to,amount,date,note->if(existing.id==0L)vm.transfer(from,to,amount,date,note) else vm.editTransfer(existing,from,to,amount,date,note);transferOpen=false;editingTransfer=null}}
}

@Composable
private fun TransactionDialog(existing:TransactionEntity?,defaultType:String,accounts:List<AccountEntity>,categories:List<CategoriaEntity>,subs:List<SubcategoryEntity>,onDismiss:()->Unit,onSave:(String,String,String,Long?,Long?,Long?,String,String?)->Unit){
    var type by remember(existing){mutableStateOf(existing?.type?:defaultType)};var desc by remember(existing){mutableStateOf(existing?.description.orEmpty())};var amount by remember(existing){mutableStateOf(existing?.let{Money.decimal(it.amountCents).toPlainString()}.orEmpty())};var date by remember(existing){mutableStateOf(existing?.date?:LocalDate.now().toString())};var note by remember(existing){mutableStateOf(existing?.observation.orEmpty())};var cat by remember(existing,categories){mutableStateOf(existing?.categoryId?:categories.firstOrNull()?.id)};var sub by remember(existing){mutableStateOf(existing?.subcategoryId)};var account by remember(existing,accounts){mutableStateOf(existing?.accountId?:accounts.firstOrNull()?.id)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"Nova transação" else "Editar transação")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row { MyExpensesFilterChip(type=="despesa",{type="despesa"},label={Text("Despesa")});Spacer(Modifier.width(8.dp));MyExpensesFilterChip(type=="receita",{type="receita"},label={Text("Receita")}) }
        OutlinedTextField(desc,{desc=it},label={Text("Descrição")},singleLine=true);OutlinedTextField(amount,{amount=it},label={Text("Valor")},singleLine=true);OutlinedTextField(date,{date=it},label={Text("Data AAAA-MM-DD")},singleLine=true)
        SelectBox("Categoria",categories.map{it.id to it.nome},cat){cat=it;sub=null};SelectBox("Subcategoria",subs.filter{it.categoryId==cat}.map{it.id to it.name},sub){sub=it};SelectBox("Conta",accounts.map{it.id to it.name},account){account=it};OutlinedTextField(note,{note=it},label={Text("Observação")},singleLine=true)
    }},confirmButton={TextButton(onClick={onSave(type,desc,amount,cat,sub,account,date,note.ifBlank{null});onDismiss()}){Text("Salvar")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}

@Composable private fun TransferDialog(accounts:List<AccountEntity>,item:TransferEntity?,onDismiss:()->Unit,onSave:(Long?,Long?,String,String,String?)->Unit){var from by remember(item,accounts){mutableStateOf(item?.sourceAccountId?:accounts.firstOrNull()?.id)};var to by remember(item,accounts){mutableStateOf(item?.destinationAccountId?:accounts.getOrNull(1)?.id)};var amount by remember(item){mutableStateOf(item?.takeIf{it.amountCents>0}?.let{Money.decimal(it.amountCents).toPlainString()}.orEmpty())};var date by remember(item){mutableStateOf(item?.date?:LocalDate.now().toString())};var note by remember(item){mutableStateOf(item?.observation.orEmpty())};AlertDialog(onDismissRequest=onDismiss,title={Text(if(item==null)"Transferência entre contas" else "Editar transferência")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){SelectBox("Origem",accounts.map{it.id to it.name},from){from=it};SelectBox("Destino",accounts.map{it.id to it.name},to){to=it};OutlinedTextField(amount,{amount=it},label={Text("Valor")});OutlinedTextField(date,{date=it},label={Text("Data")});OutlinedTextField(note,{note=it},label={Text("Observação")})}},confirmButton={TextButton(onClick={onSave(from,to,amount,date,note.ifBlank{null})}){Text(if(item==null)"Transferir" else "Salvar")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})}

@Composable fun AccountsScreen(vm:FinanceViewModel,modifier:Modifier=Modifier){
    val accounts by vm.accounts.collectAsState();val summary by vm.summary.collectAsState();val balances by vm.accountBalances.collectAsState()
    var selected by remember{mutableStateOf<AccountEntity?>(null)};var create by remember{mutableStateOf(false)};var delete by remember{mutableStateOf<AccountEntity?>(null)}
    Column(modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column{Text("SUAS FINANÇAS",style=MaterialTheme.typography.labelSmall,color=ExpensePalette.Muted,letterSpacing=1.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold);Text("Contas",style=MaterialTheme.typography.headlineSmall,color=ExpensePalette.Ink,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)};Button(onClick={selected=null;create=true},shape=RoundedCornerShape(14.dp)){Icon(Icons.Filled.Add,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("Adicionar")}}
        SurfaceCard { Column(Modifier.fillMaxWidth().padding(18.dp)){Text("Saldo total",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted);Text(Money.format(summary.accountBalanceCents),Modifier.padding(top=4.dp),style=MaterialTheme.typography.headlineSmall,color=ExpensePalette.Ink,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)} }
        LazyColumn(Modifier.weight(1f).padding(top=12.dp),contentPadding=PaddingValues(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(accounts,key={it.id}){account->
                SurfaceCard { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(ExpensePalette.PurpleLight,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Icon(Icons.Filled.AccountBalanceWallet,null,tint=ExpensePalette.Purple)};Column(Modifier.weight(1f).padding(start=12.dp)){Text(account.name,style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Ink,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold);Text(account.type.replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted)};Text(Money.format(balances[account.id]?:account.openingBalanceCents),style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)}
                    HorizontalDivider(Modifier.padding(top=12.dp),color=ExpensePalette.Border)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={selected=account;create=true}){Icon(Icons.Filled.Edit,null,modifier=Modifier.size(16.dp));Spacer(Modifier.width(4.dp));Text("Editar")};TextButton(onClick={delete=account}){Icon(Icons.Filled.DeleteOutline,null,tint=ExpensePalette.Red,modifier=Modifier.size(16.dp));Spacer(Modifier.width(4.dp));Text("Excluir",color=ExpensePalette.Red)}}
                } }
            }
        }
    }
    if(create)AccountDialog(selected,{create=false}){name,type,balance->if(selected==null)vm.createAccount(name,type,balance)else vm.updateAccount(selected!!,name,type,balance);create=false}
    delete?.let{account->AlertDialog(onDismissRequest={delete=null},title={Text("Excluir conta?")},text={Text("${account.name} será removida se não estiver vinculada a lançamentos.")},confirmButton={TextButton(onClick={vm.deleteAccount(account);delete=null}){Text("Excluir")}},dismissButton={TextButton(onClick={delete=null}){Text("Cancelar")}})}
}

@Composable private fun AccountDialog(item:AccountEntity?,dismiss:()->Unit,save:(String,String,String)->Unit){var name by remember(item){mutableStateOf(item?.name.orEmpty())};var type by remember(item){mutableStateOf(item?.type?:"corrente")};var balance by remember(item){mutableStateOf(item?.let{Money.decimal(it.openingBalanceCents).toPlainString()}?:"0")};AlertDialog(onDismissRequest=dismiss,title={Text(if(item==null)"Nova conta" else "Editar conta")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("Nome")});SelectTextBox("Tipo",listOf("corrente" to "Conta corrente","poupanca" to "Poupança","carteira" to "Carteira","investimento" to "Investimentos"),type){type=it};OutlinedTextField(balance,{balance=it},label={Text("Saldo inicial")})}},confirmButton={TextButton(onClick={save(name,type,balance)}){Text("Salvar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})}

@Composable fun PlanningScreen(vm:FinanceViewModel,modifier:Modifier=Modifier){val accounts by vm.accounts.collectAsState();val cats by vm.categories.collectAsState();val fixed by vm.fixed.collectAsState();val budgets by vm.budgets.collectAsState();val planning by vm.planning.collectAsState();val salary by vm.salary.collectAsState();var income by remember(planning){mutableStateOf(planning?.let{Money.decimal(it.incomeCents).toPlainString()}.orEmpty())};var savings by remember(planning){mutableStateOf(planning?.let{java.math.BigDecimal.valueOf(it.savingsPercent.toLong(),2).stripTrailingZeros().toPlainString()}.orEmpty())};var budgetAmount by remember{mutableStateOf("")};var budgetCategory by remember{mutableStateOf<Long?>(null)};var fixedOpen by remember{mutableStateOf(false)};var fixedEditing by remember{mutableStateOf<FixedExpenseEntity?>(null)};var fixedDelete by remember{mutableStateOf<FixedExpenseEntity?>(null)};var salaryAmount by remember(salary){mutableStateOf(salary?.let{Money.decimal(it.amountCents).toPlainString()}.orEmpty())};var salaryDay by remember(salary){mutableStateOf(salary?.dueDay?.toString().orEmpty())};var salaryAccount by remember(salary,accounts){mutableStateOf(salary?.accountId?:accounts.firstOrNull()?.id)}
    LazyColumn(modifier.fillMaxSize().padding(horizontal=18.dp),contentPadding=PaddingValues(top=8.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{SurfaceCard{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Organização",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted);Text("Planejamento do mês",style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Ink);OutlinedTextField(income,{income=it},modifier=Modifier.fillMaxWidth(),label={Text("Renda mensal")});OutlinedTextField(savings,{savings=it},modifier=Modifier.fillMaxWidth(),label={Text("Percentual de economia")});Button(onClick={vm.savePlan(income,savings)},modifier=Modifier.fillMaxWidth()){Text("Salvar plano")}}}}
        item{SurfaceCard{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Controle por tipo de gasto",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted);Text("Limites por categoria",style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Ink);OutlinedTextField(budgetAmount,{budgetAmount=it},modifier=Modifier.fillMaxWidth(),label={Text("Limite")});SelectBox("Categoria (vazio = geral)",listOf(0L to "Limite geral")+cats.filter{it.ativa}.map{it.id to it.nome},budgetCategory?:0L){budgetCategory=it.takeIf{it!=0L}};Button(onClick={vm.saveBudget(budgetCategory,budgetAmount)},modifier=Modifier.fillMaxWidth()){Text("Salvar limite")}}}}
        items(budgets,key={it.id}){b->SurfaceCard{Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(b.legacyCategoryName?:if(b.categoryKey=="*")"Limite geral" else cats.firstOrNull{c->c.id==b.categoryId}?.nome.orEmpty(),style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink);Text(Money.format(b.amountCents),color=ExpensePalette.Muted)};TextButton(onClick={vm.deleteBudget(b)}){Text("Remover",color=ExpensePalette.Red)}}}}
        item{SurfaceCard{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Receita recorrente",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted);Text("Salário",style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Ink);OutlinedTextField(salaryAmount,{salaryAmount=it},modifier=Modifier.fillMaxWidth(),label={Text("Valor mensal")});OutlinedTextField(salaryDay,{salaryDay=it},modifier=Modifier.fillMaxWidth(),label={Text("Dia do pagamento (1–31)")});SelectBox("Conta de recebimento",accounts.map{it.id to it.name},salaryAccount){salaryAccount=it};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={vm.saveSalary(salaryAmount,salaryDay,salaryAccount,salary?.active?:true)},modifier=Modifier.weight(1f)){Text("Salvar salário")};TextButton(onClick={vm.removeSalary()}){Text("Remover")}}}}}
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Despesas fixas",style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Ink);Text("Contas que vencem todo mês",style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted)};TextButton(onClick={fixedOpen=true}){Text("Adicionar")}}}
        items(fixed,key={"f${it.id}"}){f->SurfaceCard{Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(f.description,style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink);Text("Vencimento no dia ${f.dueDay}",style=MaterialTheme.typography.bodySmall,color=ExpensePalette.Muted)};Text(Money.format(f.amountCents),style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Red)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={vm.payFixed(f)}){Text("Marcar paga")};TextButton(onClick={fixedEditing=f}){Text("Editar")};TextButton(onClick={fixedDelete=f}){Text("Excluir",color=ExpensePalette.Red)}}}}}
    };if(fixedOpen||fixedEditing!=null)FixedDialog(accounts,cats,fixedEditing,{fixedOpen=false;fixedEditing=null}){d,a,c,acct,day->if(fixedEditing==null)vm.createFixed(d,a,c,acct,day)else vm.updateFixed(fixedEditing!!,d,a,c,acct,day);fixedOpen=false;fixedEditing=null};fixedDelete?.let{item->AlertDialog(onDismissRequest={fixedDelete=null},title={Text("Excluir despesa fixa?")},confirmButton={TextButton(onClick={vm.deleteFixed(item);fixedDelete=null}){Text("Excluir")}},dismissButton={TextButton(onClick={fixedDelete=null}){Text("Cancelar")}})}}

@Composable private fun FixedDialog(accounts:List<AccountEntity>,cats:List<CategoriaEntity>,item:FixedExpenseEntity?,dismiss:()->Unit,save:(String,String,Long?,Long?,String)->Unit){var desc by remember(item){mutableStateOf(item?.description.orEmpty())};var amount by remember(item){mutableStateOf(item?.let{Money.decimal(it.amountCents).toPlainString()}.orEmpty())};var category by remember(item,cats){mutableStateOf(item?.categoryId?:cats.firstOrNull()?.id)};var account by remember(item,accounts){mutableStateOf(item?.accountId?:accounts.firstOrNull()?.id)};var day by remember(item){mutableStateOf(item?.dueDay?.toString()?:"1")};AlertDialog(onDismissRequest=dismiss,title={Text(if(item==null)"Despesa fixa" else "Editar despesa fixa")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(desc,{desc=it},label={Text("Descrição")});OutlinedTextField(amount,{amount=it},label={Text("Valor")});SelectBox("Categoria",cats.map{it.id to it.nome},category){category=it};SelectBox("Conta",accounts.map{it.id to it.name},account){account=it};OutlinedTextField(day,{day=it},label={Text("Dia do vencimento")})}},confirmButton={TextButton(onClick={save(desc,amount,category,account,day)}){Text("Salvar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})}

@Composable fun ReportsScreen(vm:FinanceViewModel,modifier:Modifier=Modifier){
    var annual by remember{mutableStateOf(false)};var expense by remember{mutableStateOf(true)};var year by remember{mutableStateOf(YearMonth.now().year.toString())};var month by remember{mutableStateOf(YearMonth.now().monthValue.toString())};val report by vm.report.collectAsState()
    LaunchedEffect(annual,expense,year,month){vm.loadReport(year.toIntOrNull()?:YearMonth.now().year,if(annual)null else month.toIntOrNull()?:YearMonth.now().monthValue,if(expense)"despesa" else "receita")}
    Column(modifier.fillMaxSize().padding(horizontal=18.dp)){
        Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){MyExpensesFilterChip(annual,{annual=true},label={Text("Anual")});MyExpensesFilterChip(!annual,{annual=false},label={Text("Mensal")})}
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){MyExpensesFilterChip(expense,{expense=true},label={Text("Despesas")});MyExpensesFilterChip(!expense,{expense=false},label={Text("Renda")})}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(year,{year=it},modifier=Modifier.weight(1f),label={Text("Ano")},singleLine=true);if(!annual)OutlinedTextField(month,{month=it},modifier=Modifier.weight(1f),label={Text("Mês")},singleLine=true)}
        SurfaceCard {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Column(Modifier.weight(1f)){Text("Despesas",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted);Text(Money.format(report?.expenseCents?:0),style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Red)}
                    Column(Modifier.weight(1f)){Text("Renda",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted);Text(Money.format(report?.incomeCents?:0),style=MaterialTheme.typography.titleMedium,color=ExpensePalette.Green)}
                }
                HorizontalDivider(color=ExpensePalette.Border)
                val balance=(report?.incomeCents?:0L)-(report?.expenseCents?:0L)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Balanço",style=MaterialTheme.typography.labelMedium,color=ExpensePalette.Muted);Text((if(balance>=0)"+ " else "− ")+Money.format(kotlin.math.abs(balance)),style=MaterialTheme.typography.titleMedium,color=if(balance>=0)ExpensePalette.Green else ExpensePalette.Red)}
            }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(top=4.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{SectionHeading(if(expense)"Despesas por categoria" else "Renda por categoria")}
            item{SurfaceCard{CategoryDonut(report?.byCategory.orEmpty(),if(expense)report?.expenseCents?:0 else report?.incomeCents?:0,if(expense)"em despesas" else "em renda")}}
            item{SectionHeading("Por subcategoria")}
            items(report?.bySubcategory?.toList().orEmpty().sortedByDescending{it.second}){entry->SurfaceCard{Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(entry.first,color=ExpensePalette.Ink);Text(Money.format(entry.second),color=ExpensePalette.Muted)}}}
        }
    }
}

@Composable fun CategoryManagementScreen(vm:FinanceViewModel,modifier:Modifier=Modifier){
    val categories by vm.categories.collectAsState();val subs by vm.subcategories.collectAsState();var name by remember{mutableStateOf("")};var subName by remember{mutableStateOf("")};var category by remember(categories){mutableStateOf(categories.firstOrNull{it.ativa}?.id)};var editing by remember{mutableStateOf<CategoriaEntity?>(null)}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Suas categorias",style=MaterialTheme.typography.titleLarge,color=ExpensePalette.Ink)
        Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,ExpensePalette.Border)){
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Nova categoria",style=MaterialTheme.typography.titleSmall,color=ExpensePalette.Ink)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(name,{name=it},modifier=Modifier.weight(1f),singleLine=true,placeholder={Text("Nome da categoria")});Button(onClick={vm.addCategory(name);name=""},modifier=Modifier.height(56.dp),contentPadding=PaddingValues(horizontal=14.dp)){Text("Adicionar",maxLines=1)} }
                HorizontalDivider(color=ExpensePalette.Border)
                SelectBox("Categoria da subcategoria",categories.filter{it.ativa}.map{it.id to it.nome},category){category=it}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(subName,{subName=it},modifier=Modifier.weight(1f),singleLine=true,placeholder={Text("Nome da subcategoria")});Button(onClick={vm.addSubcategory(category,subName);subName=""},modifier=Modifier.height(56.dp),contentPadding=PaddingValues(horizontal=14.dp)){Text("Adicionar",maxLines=1)} }
            }
        }
        categories.forEach{cat->
            Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=androidx.compose.foundation.BorderStroke(1.dp,ExpensePalette.Border)){
                Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                        Column(Modifier.weight(1f)){
                            TextButton(onClick={editing=cat},contentPadding=PaddingValues(horizontal=4.dp,vertical=6.dp)){
                                Column(horizontalAlignment=Alignment.Start){Text(cat.nome,maxLines=1,overflow=TextOverflow.Ellipsis,color=ExpensePalette.Ink,style=MaterialTheme.typography.titleSmall);Text(if(cat.ativa)"Ativa" else "Arquivada",color=ExpensePalette.Muted,style=MaterialTheme.typography.bodySmall)}
                            }
                        }
                        if(cat.origem=="PERSONALIZADA")TextButton(onClick={vm.updateCategoryActive(cat,!cat.ativa)},contentPadding=PaddingValues(horizontal=8.dp)){Text(if(cat.ativa)"Arquivar" else "Reativar",color=ExpensePalette.Purple)}
                    }
                    if(cat.ativa)subs.filter{it.categoryId==cat.id}.forEach{sub->Row(Modifier.fillMaxWidth().padding(start=8.dp),verticalAlignment=Alignment.CenterVertically){Text("• ${sub.name}",Modifier.weight(1f),color=ExpensePalette.Muted,style=MaterialTheme.typography.bodyMedium);TextButton(onClick={vm.deleteSubcategory(sub.id)},contentPadding=PaddingValues(horizontal=8.dp)){Text("Remover",color=ExpensePalette.Red)}}}
                }
            }
        }
    }
    editing?.let{cat->var edited by remember(cat){mutableStateOf(cat.nome)};AlertDialog(onDismissRequest={editing=null},title={Text("Editar categoria")},text={Column{OutlinedTextField(edited,{edited=it},label={Text("Nome")});Text("Personalizadas podem ser arquivadas; categorias padrão mantêm o nome original.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(enabled=cat.origem=="PERSONALIZADA",onClick={vm.renameCategory(cat,edited);editing=null}){Text("Renomear")}},dismissButton={TextButton(onClick={editing=null}){Text("Fechar")}})}
}

@Composable private fun SelectBox(label:String,options:List<Pair<Long,String>>,selected:Long?,onSelect:(Long)->Unit){var expanded by remember{mutableStateOf(false)};OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("$label: ${options.firstOrNull{it.first==selected}?.second?:"Selecionar"}")};DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onSelect(id);expanded=false})}}}


@Composable private fun SelectTextBox(label:String,options:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit){var expanded by remember{mutableStateOf(false)};OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("$label: ${options.firstOrNull{it.first==selected}?.second?:"Selecionar"}")};DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onSelect(id);expanded=false})}}}
