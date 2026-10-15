package br.com.conde.confiavel;

/**
 * VOLTA N: o receptor so aceita em ordem, e o emissor volta ao primeiro nao confirmado.
 *
 * <p>Ele mantem varios quadros no ar ao mesmo tempo, que e o que resolve o enlace parado da
 * parada e espera. O receptor continua simples: ele nao guarda nada fora de ordem, entao o
 * que chegar adiantado e jogado fora.
 *
 * <p>A confirmacao e ACUMULADA: confirmar o numero n quer dizer "recebi tudo ate n". Isso
 * torna o protocolo imune a confirmacao perdida, porque a proxima confirma as anteriores, e
 * e tambem o que a tabela do reconhecimento mostra que ele esconde.
 *
 * <p>A maior janela aqui e {@code espaco - 1}. Com a janela do tamanho do espaco inteiro, o
 * receptor nao distingue a janela nova da antiga: ele veria o numero zero de novo e nao
 * saberia se e o byte zero retransmitido ou o que vem depois da volta inteira.
 */
public final class VoltaN extends Protocolo {

    private int esperado;

    @Override
    public String nome() {
        return "volta N";
    }

    @Override
    public int maiorJanela(int espaco) {
        return espaco - 1;
    }

    @Override
    protected void aoChegarDado(Quadro quadro, int fatia, Canal volta) {
        if (quadro.numero() == Math.floorMod(esperado, espaco)) {
            recebido.write(quadro.carga());

            esperado++;
        }

        /* Confirma o ultimo recebido em ordem, mesmo que o quadro que chegou nao sirva. */
        volta.mandar(Quadro.confirmando(Math.floorMod(esperado - 1, espaco)), fatia);
    }

    @Override
    protected void aoChegarConfirmacao(Quadro quadro) {
        if (base >= fluxo.length) return;

        int quanto = Math.floorMod(quadro.numero() - Math.floorMod(base, espaco), espaco);

        if (quanto >= janela) return;

        base = Math.min(fluxo.length, base + quanto + 1);
    }

    @Override
    protected int aMandar(int fatia) {
        if (base >= fluxo.length) return -1;

        /* O tempo esgotou para o primeiro nao confirmado: volta e manda tudo de novo. */
        if (mandadoEm[base] != Integer.MIN_VALUE / 2 && esgotou(base, fatia)) proximo = base;

        if (proximo >= fluxo.length) return -1;

        if (proximo - base >= janela) return -1;

        return proximo;
    }
}
