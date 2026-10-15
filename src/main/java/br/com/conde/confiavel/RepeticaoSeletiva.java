package br.com.conde.confiavel;

/**
 * REPETICAO SELETIVA: o receptor guarda o que chega fora de ordem, e o emissor repete so o
 * que faltou.
 *
 * <p>Ele e o unico dos tres que nao joga trabalho fora. O preco e que os dois lados precisam
 * de memoria para uma janela inteira e de um relogio por quadro, e que a confirmacao passa a
 * ser individual: confirmar o numero n diz apenas que n chegou.
 *
 * <p>A maior janela aqui e METADE do espaco de numeracao, e essa e a regra que a tabela da
 * numeracao mede. Com janela maior, a janela de recepcao nova encosta na antiga: um quadro
 * retransmitido do bloco anterior cai dentro da janela de agora, e o receptor o guarda como
 * se fosse dado novo. O fluxo entregue sai errado sem que nada no protocolo acuse.
 */
public final class RepeticaoSeletiva extends Protocolo {

    private int esperado;

    private byte[] guardado;
    private boolean[] temGuardado;

    private boolean[] confirmado;

    @Override
    public String nome() {
        return "repeticao seletiva";
    }

    @Override
    public int maiorJanela(int espaco) {
        return espaco / 2;
    }

    @Override
    protected void comecar() {
        guardado = new byte[fluxo.length + espaco];
        temGuardado = new boolean[fluxo.length + espaco];
        confirmado = new boolean[fluxo.length];
    }

    @Override
    protected void aoChegarDado(Quadro quadro, int fatia, Canal volta) {
        /* Onde este numero cai, SUPONDO que ele e desta janela de recepcao. Essa suposicao
         * e justamente a que deixa de valer quando a janela passa de metade do espaco. */
        int onde = esperado
                + Math.floorMod(quadro.numero() - Math.floorMod(esperado, espaco), espaco);

        if (dentroDaJanela(quadro.numero(), Math.floorMod(esperado, espaco), janela, espaco)
                && onde < guardado.length && !temGuardado[onde]) {

            guardado[onde] = quadro.carga();
            temGuardado[onde] = true;

            while (esperado < guardado.length && temGuardado[esperado]) {
                recebido.write(guardado[esperado]);

                esperado++;
            }
        }

        volta.mandar(Quadro.confirmando(quadro.numero()), fatia);
    }

    @Override
    protected void aoChegarConfirmacao(Quadro quadro) {
        int onde = base + Math.floorMod(quadro.numero() - Math.floorMod(base, espaco), espaco);

        if (onde >= fluxo.length || onde - base >= janela) return;

        confirmado[onde] = true;

        while (base < fluxo.length && confirmado[base]) base++;
    }

    @Override
    protected int aMandar(int fatia) {
        if (base >= fluxo.length) return -1;

        int fim = Math.min(fluxo.length, base + janela);

        /* Primeiro o que esgotou o tempo, do mais antigo para o mais novo. */
        for (int qual = base; qual < fim; qual++) {
            if (confirmado[qual]) continue;

            if (mandadoEm[qual] == Integer.MIN_VALUE / 2) continue;

            if (esgotou(qual, fatia)) return qual;
        }

        /* Depois o que ainda nao foi mandado nenhuma vez. */
        for (int qual = base; qual < fim; qual++)
            if (!confirmado[qual] && mandadoEm[qual] == Integer.MIN_VALUE / 2) return qual;

        return -1;
    }
}
