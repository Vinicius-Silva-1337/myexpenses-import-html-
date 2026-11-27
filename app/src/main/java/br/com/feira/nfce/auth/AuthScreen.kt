package br.com.feira.nfce.auth

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.feira.nfce.MainViewModel
import br.com.feira.nfce.R
import br.com.feira.nfce.ui.ExpensePalette
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var registering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var revealPassword by remember { mutableStateOf(false) }
    var revealConfirmation by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val transition = rememberInfiniteTransition(label = "auth-glow")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(8000), RepeatMode.Reverse), label = "drift")
    val salaryTransition = rememberInfiniteTransition(label = "salary-float")
    val salaryFloat by salaryTransition.animateFloat(-5f, 5f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "salary-y")
    val marketTransition = rememberInfiniteTransition(label = "market-float")
    val marketFloat by marketTransition.animateFloat(5f, -5f, infiniteRepeatable(tween(4100, delayMillis = 550), RepeatMode.Reverse), label = "market-y")
    val darkScheme = darkColorScheme(
        primary = Color(0xFF9B75FF), onPrimary = Color.White,
        secondary = Color(0xFFE879F9), background = Color(0xFF0D0820),
        surface = Color(0xFF17112B), onSurface = Color.White,
        onBackground = Color.White, error = Color(0xFFFCA5A5)
    )
    MaterialTheme(colorScheme = darkScheme, shapes = MaterialTheme.shapes) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0D0820)).imePadding()) {
            Box(Modifier.offset(x=(-110).dp + (drift * 28).dp, y=(-130).dp).size(320.dp).background(Brush.radialGradient(listOf(ExpensePalette.Purple.copy(alpha=.72f),ExpensePalette.Purple.copy(alpha=.05f),Color.Transparent)),CircleShape))
            Box(Modifier.align(Alignment.BottomEnd).offset(x=100.dp,y=125.dp-(drift*32).dp).size(330.dp).background(Brush.radialGradient(listOf(ExpensePalette.Magenta.copy(alpha=.5f),ExpensePalette.Magenta.copy(alpha=.03f),Color.Transparent)),CircleShape))
            Box(Modifier.align(Alignment.CenterEnd).offset(x=130.dp,y=(-maxHeight*.1f)).size(240.dp).background(Brush.radialGradient(listOf(ExpensePalette.Cyan.copy(alpha=.24f),Color.Transparent)),CircleShape))
            FloatingFinanceCard(
                title = "Salário",
                amount = "+ R$ 2.300",
                positive = true,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 14.dp)
                    .graphicsLayer { translationY = salaryFloat.dp.toPx() }
            )
            FloatingFinanceCard(
                title = "Mercado",
                amount = "− R$ 186",
                positive = false,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 60.dp)
                    .graphicsLayer { translationY = marketFloat.dp.toPx() }
            )
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.my_expenses_logo),
                    contentDescription = "Logo My Expenses",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1754f / 1240f)
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.widthIn(max=400.dp).fillMaxWidth().padding(bottom=20.dp)
                        .shadow(28.dp,RoundedCornerShape(30.dp),ambientColor=Color.Black.copy(alpha=.5f))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.12f),Color.White.copy(alpha=.045f))))
                        .border(1.dp,Color.White.copy(alpha=.16f),RoundedCornerShape(30.dp))
                        .padding(horizontal=24.dp,vertical=22.dp),
                ) {
                    Text(if(registering)"Crie sua conta" else "Bem-vindo de volta",Modifier.fillMaxWidth(),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Color.White)
                    Text(if(registering)"Leva menos de um minuto." else "Entre para ver como está o seu mês.",Modifier.fillMaxWidth().padding(top=4.dp,bottom=18.dp),style=MaterialTheme.typography.bodyMedium,color=Color(0xFFC9C3E6))
                    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha=.08f)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        AuthTab("Login",!registering,Modifier.weight(1f)){registering=false;message=null}
                        AuthTab("Inscreva-se",registering,Modifier.weight(1f)){registering=true;message=null}
                    }
                    Spacer(Modifier.height(18.dp))
                    if(registering){AuthLabel("Nome");AuthField(name,{name=it},"Seu nome",Icons.Filled.Person,KeyboardType.Text)}
                    AuthLabel("E-mail");AuthField(email,{email=it},"voce@email.com",Icons.Filled.Email,KeyboardType.Email)
                    AuthLabel("Senha");AuthField(password,{password=it},if(registering)"Mínimo de 8 caracteres" else "Sua senha",Icons.Filled.Lock,KeyboardType.Password,secret=true,revealed=revealPassword,onToggle={revealPassword=!revealPassword})
                    if(registering){AuthLabel("Confirmar senha");AuthField(confirmation,{confirmation=it},"Repita a senha",Icons.Filled.Lock,KeyboardType.Password,secret=true,revealed=revealConfirmation,onToggle={revealConfirmation=!revealConfirmation})}
                    message?.let { Text(it,Modifier.fillMaxWidth().padding(vertical=8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEF4444).copy(alpha=.18f)).border(1.dp,Color(0xFFF87171).copy(alpha=.35f),RoundedCornerShape(12.dp)).padding(12.dp),color=Color(0xFFFECACA),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold) }
                    Button(
                        enabled=!working,
                        onClick={
                            if(registering && password!=confirmation){message="As senhas não coincidem.";return@Button}
                            working=true;message=null
                            scope.launch {
                                val result=if(registering)viewModel.cadastrar(name,email,password.toCharArray()) else viewModel.entrar(email,password.toCharArray())
                                message=if(result is AuthResult.Failure)result.message else null
                                password="";confirmation="";working=false
                            }
                        },
                        modifier=Modifier.fillMaxWidth().padding(top=8.dp).height(54.dp),
                        shape=RoundedCornerShape(14.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=ExpensePalette.Purple)
                    ) { Text(if(working)"Aguarde…" else if(registering)"Criar conta" else "Entrar",fontWeight=FontWeight.ExtraBold,fontSize=16.sp) }
                    Text(if(registering)"Seus dados ficam no aparelho." else "Acesse suas finanças com segurança local.",Modifier.align(Alignment.CenterHorizontally).padding(top=18.dp),color=Color(0xFFC9C3E6),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FloatingFinanceCard(title: String, amount: String, positive: Boolean, modifier: Modifier = Modifier) {
    val accent = if (positive) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
    val badge = if (positive) "↑" else "↓"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = .10f))
            .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(22.dp).background(accent.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) {
            Text(badge, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title, color = Color.White.copy(alpha = .86f), fontSize = 11.sp, lineHeight = 13.sp)
            Text(amount, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun AuthTab(label:String,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit){TextButton(onClick=onClick,modifier=modifier.height(42.dp),shape=CircleShape,colors=ButtonDefaults.textButtonColors(containerColor=if(selected)ExpensePalette.Purple else Color.Transparent,contentColor=if(selected)Color.White else Color(0xFFC9C3E6))){Text(label,fontWeight=FontWeight.Bold)} }

@Composable private fun AuthLabel(text:String){Text(text,Modifier.fillMaxWidth().padding(bottom=6.dp),color=Color(0xFFE4E0F5),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)}

@Composable private fun AuthField(value:String,onValueChange:(String)->Unit,hint:String,icon:androidx.compose.ui.graphics.vector.ImageVector,keyboardType:KeyboardType,secret:Boolean=false,revealed:Boolean=false,onToggle:(()->Unit)?=null){
    OutlinedTextField(
        value=value,onValueChange=onValueChange,modifier=Modifier.fillMaxWidth().padding(bottom=12.dp),singleLine=true,
        placeholder={Text(hint,color=Color(0xFF8B83B4))},
        leadingIcon={Icon(icon,contentDescription=null,tint=Color(0xFFA99FD6))},
        trailingIcon=if(secret&&onToggle!=null){{IconButton(onClick=onToggle){Icon(if(revealed)Icons.Filled.VisibilityOff else Icons.Filled.Visibility,contentDescription=if(revealed)"Ocultar senha" else "Mostrar senha",tint=Color(0xFFCFC8EE))}}}else null,
        visualTransformation=if(secret&&!revealed)PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=keyboardType),
        shape=RoundedCornerShape(14.dp),
        colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=Color(0xFFA78BFA),unfocusedBorderColor=Color.White.copy(alpha=.14f),focusedContainerColor=Color.White.copy(alpha=.1f),unfocusedContainerColor=Color.White.copy(alpha=.07f),cursorColor=Color(0xFFC4B5FD),focusedLeadingIconColor=Color(0xFFC4B5FD),unfocusedLeadingIconColor=Color(0xFFA99FD6))
    )
}
