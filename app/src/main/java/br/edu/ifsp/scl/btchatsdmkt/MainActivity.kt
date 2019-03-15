package br.edu.ifsp.scl.btchatsdmkt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.PermissionChecker
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import br.edu.ifsp.scl.btchatsdmkt.BluetoothSingleton.Constantes.ATIVA_BLUETOOTH
import br.edu.ifsp.scl.btchatsdmkt.BluetoothSingleton.Constantes.ATIVA_DESCOBERTA_BLUETOOTH
import br.edu.ifsp.scl.btchatsdmkt.BluetoothSingleton.Constantes.MENSAGEM_DESCONEXAO
import br.edu.ifsp.scl.btchatsdmkt.BluetoothSingleton.Constantes.MENSAGEM_TEXTO
import br.edu.ifsp.scl.btchatsdmkt.BluetoothSingleton.Constantes.REQUER_PERMISSOES_LOCALIZACAO
import br.edu.ifsp.scl.btchatsdmkt.BluetoothSingleton.Constantes.TEMPO_DESCOBERTA_SERVICO_BLUETOOTH
import br.edu.ifsp.scl.btchatsdmkt.BluetoothSingleton.adaptadorBt
import br.edu.ifsp.scl.btchatsdmkt.BluetoothSingleton.outputStream
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_activity.*
import java.io.IOException

class  MainActivity : AppCompatActivity() {
    // Referências para as threads filhas
    private var threadServidor: ThreadServidor? = null
    private var threadCliente: ThreadCliente? = null
    private var threadComunicacao: ThreadComunicacao? = null

    // Lista de Disspositivos
    var listaBtsEncontrados: MutableList<BluetoothDevice>? = null

    // BroadcastReceiver para eventos descoberta e finalização de busca
    private var eventosBtReceiver: EventosBluetoothReceiver? = null

    // Adapter que atualiza a lista de mensagens
    var historicoAdapter: ArrayAdapter<String>? = null

    // Handler da tela principal
    var mHandler: TelaPrincipalHandler? = null

    //Indica modo de execucao
    var modoServidor: Boolean? = null

    //Nome do remetente
    var nomeRemetente = "Eu"

    // Dialog para aguardar conexões e busca
    private var aguardeDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        historicoAdapter = ArrayAdapter(this,android.R.layout.simple_list_item_1)
        historicoListView.adapter = historicoAdapter

        mHandler = TelaPrincipalHandler()

        // Pegando referência para adaptador Bt
        pegandoAdaptadorBt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ((ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)   != PermissionChecker.PERMISSION_GRANTED)  ||
                (ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION) != PermissionChecker.PERMISSION_GRANTED)) {
                   ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),REQUER_PERMISSOES_LOCALIZACAO)
               }
        }

        showDialogModoOperacao()
    }

    fun showDialogModoOperacao(){
        //Inflando layout do dialog
        val optDialogView = LayoutInflater.from(this).inflate(R.layout.dialog_activity, null)

        //Builder do dialog com as opções
        val optBuilder = AlertDialog.Builder(this).setView(optDialogView).setTitle("Modo de operação:")

        //Show do dialog
        val optDialog = optBuilder.show()
        optDialog.setCanceledOnTouchOutside(false)

        //Eventos radio button dialog
        optDialog.radioServidor.setOnClickListener{ onRadioButtonOptClicked(view = it) }
        optDialog.radioCliente.setOnClickListener{ onRadioButtonOptClicked(view = it) }

        //Eventos do dialog
        optDialog.btnSalvarConf.setOnClickListener{
            if(modoServidor != null)
            {
                optDialog.dismiss()
                inicializa()
            }
            else
                toast( "Selecione um modo de operação")
        }
    }

    fun showDialogNomeRemetente()
    {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Atualizar nome de remetente")
        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.dialog_nome_activity, null)
        val editText  = dialogLayout.findViewById<EditText>(R.id.nomeRemententeEditText)
        builder.setView(dialogLayout)
        builder.setPositiveButton("SALVAR") { dialogInterface, i ->
            nomeRemetente =  editText.text.toString()
            Toast.makeText(applicationContext, "Nome de remetente alterado para: $nomeRemetente", Toast.LENGTH_SHORT).show()
        }
        builder.show()
    }

    fun inicializa()
    {
        if(modoServidor!!)
            setaModoServidor()
        else
            setaModoCliente()
    }

    fun onRadioButtonOptClicked(view: View) {

        if (view is RadioButton) {
            // Check which radio button was clicked
            when (view.getId()) {

                R.id.radioServidor ->  modoServidor = view.isChecked

                R.id.radioCliente -> modoServidor = !view.isChecked
            }
        }
    }

    private fun pegandoAdaptadorBt() {
        adaptadorBt = BluetoothAdapter.getDefaultAdapter()
        if (adaptadorBt != null) {
            if (!adaptadorBt!!.isEnabled) {
                val ativaBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(ativaBluetoothIntent,ATIVA_BLUETOOTH)
            }
        }
        else {
            toast("Adaptador Bt não disponível")
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUER_PERMISSOES_LOCALIZACAO) {
            for (i in 0..grantResults.size -1) {
                if (grantResults[i] != PermissionChecker.PERMISSION_GRANTED) {
                    toast( "Permissões são necessárias")
                    finish()
                }
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ATIVA_BLUETOOTH) {
            if (resultCode != Activity.RESULT_OK) {
                toast( "Bluetooth necessário")
            }
        }
        else {
            if (requestCode == ATIVA_DESCOBERTA_BLUETOOTH) {
                if (resultCode == AppCompatActivity.RESULT_CANCELED) {
                    toast("Visibilidade necessária")
                    finish()
                } else {
                    iniciaThreadServidor()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_modo_aplicativo,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        var retorno = false
        when (item?.itemId) {
 /*           R.id.modoClienteMenuItem -> {
                setaModoCliente()
                retorno = true
            }
            R.id.modoServidorMenuItem -> {
                setaModoServidor()
                retorno = true
            }*/
            R.id.refreshMenuItem ->  {
                inicializa()
                retorno = true
            }

            R.id.nomeRemententeMenuItem ->  {
                showDialogNomeRemetente()
                retorno = true
            }
        }
        return retorno
    }
    fun setaModoCliente(){
                // (Re)Inicializando a Lista de dispositivos encontrados
        listaBtsEncontrados = mutableListOf()

        registraReceiver()

        adaptadorBt?.startDiscovery()

        exibirAguardeDialog("Procurando dispositivos Bluetooth", 0)
    }
    fun setaModoServidor()  {
        val descobertaIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        descobertaIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,TEMPO_DESCOBERTA_SERVICO_BLUETOOTH)
        startActivityForResult(descobertaIntent,ATIVA_DESCOBERTA_BLUETOOTH)
    }

    private fun registraReceiver() {
        eventosBtReceiver = eventosBtReceiver?: EventosBluetoothReceiver(this)
        registerReceiver(eventosBtReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(eventosBtReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
    }

    fun desregistraReceiver() = eventosBtReceiver?.let{unregisterReceiver(it)}

    private fun exibirAguardeDialog(mensagem:String, tempo: Int) {
        aguardeDialog = ProgressDialog.show(this,"Aguarde",mensagem,true,true) {onCancelDialog(it)}
        aguardeDialog?.show()

        if (tempo > 0) {
            mHandler?.postDelayed({
                if (threadComunicacao == null) {
                    aguardeDialog?.dismiss()
                }
            }, tempo * 1000L)
        }
    }
    private fun onCancelDialog(dialogInterface: DialogInterface){
        //Fianalizando a busca por servidores
        adaptadorBt?.cancelDiscovery()

        paraThreadFilhas()

    }

    private fun paraThreadFilhas(){
        if(threadComunicacao != null){
            threadComunicacao?.parar()
            threadComunicacao = null
        }

        if(threadCliente != null){
            threadCliente?.parar()
            threadCliente = null
        }

        if(threadServidor != null){
            threadServidor?.parar()
            threadServidor = null
        }
    }

    override fun onDestroy() {
        //Desregistrar o receiver
        desregistraReceiver()

        //Parar todas threads filhas
        paraThreadFilhas()

        super.onDestroy()
    }

    private fun iniciaThreadServidor(){
        paraThreadFilhas()
        exibirAguardeDialog("Aguardando conexões", TEMPO_DESCOBERTA_SERVICO_BLUETOOTH)
        threadServidor = ThreadServidor(this)
        threadServidor?.iniciar()
    }

    private fun iniciaThreadCliente(i:Int){
        paraThreadFilhas()

        threadCliente = ThreadCliente(this)
        threadCliente?.iniciar(listaBtsEncontrados?.get(i))
    }

    fun exibirDispositivosEncontrados(){
        aguardeDialog?.dismiss()

        if(listaBtsEncontrados?.size!! > 0)
        {
            val listaNomeBtsEncontrados: MutableList<String> = mutableListOf()
            listaBtsEncontrados?.forEach{ listaNomeBtsEncontrados.add(if(it.name == null) "Sem nome" else it.name)}

            val escolhaDispositivoDialog = with(AlertDialog.Builder(this)){
                setTitle("Dispositivos encontrados")
                setSingleChoiceItems(listaNomeBtsEncontrados.toTypedArray(),
                    -1
                ) { dialog, which ->  trataSelecaoServidor(dialog, which)}
            }

            escolhaDispositivoDialog.show()
        }
        else
        {
            val escolhaDispositivoDialog = with(AlertDialog.Builder(this)){
                setTitle("Dispositivos encontrados")
                setMessage("Nenhum dispositivo encontrado")

            }

            escolhaDispositivoDialog.show()
        }
    }

    private fun trataSelecaoServidor(dialog: DialogInterface, which: Int){
        iniciaThreadCliente(which)

        adaptadorBt?.cancelDiscovery()

        dialog.dismiss()
    }

    fun enviarMensagem(view: View){
        if(view == enviarBt){
            val mensagem = mensagemEditText.text.toString()
            mensagemEditText.setText("")


            try {
                if(outputStream != null)
                {
                    outputStream?.writeUTF("${nomeRemetente}: ${mensagem}")
                    historicoAdapter?.add("${nomeRemetente}: ${mensagem}")
                    historicoAdapter?.notifyDataSetChanged()
                }
            }
            catch (e: IOException)
            {
                mHandler?.obtainMessage(MENSAGEM_DESCONEXAO, e.message + "[0]")?.sendToTarget()

                e.printStackTrace()
            }

        }
    }

    fun trataSocket(socket:BluetoothSocket?){
        aguardeDialog?.dismiss()
        threadComunicacao = ThreadComunicacao(this)
        threadComunicacao?.iniciar(socket)
    }

    private fun toast(mensagem: String) = Toast.makeText(this,mensagem,Toast.LENGTH_SHORT).show()

    inner class TelaPrincipalHandler: Handler() {
        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)

            if (msg?.what == MENSAGEM_TEXTO) {
                historicoAdapter?.add(msg.obj.toString())
                historicoAdapter?.notifyDataSetChanged()
            }
            else {
                if (msg?.what == MENSAGEM_DESCONEXAO) {
                    toast("Desconectou: ${msg.obj}")
                }
            }
        }
    }
}
