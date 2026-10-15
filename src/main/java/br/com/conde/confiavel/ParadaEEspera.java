package br.com.conde.confiavel;

/**
 * PARADA E ESPERA: manda um, espera a confirmacao, manda o proximo.
 *
 * <p>E o protocolo mais simples que funciona, e ele funciona mesmo: com dois numeros, zero e
 * um, ele entrega o fluxo certo com qualquer taxa de perda. O numero existe so para o
 * receptor distinguir "o proximo" de "o mesmo de novo", e para isso um bit basta.
 *
 * <p>O preco esta na tabela do enchimento. Enquanto o quadro vai e a confirmacao volta, o
 * emissor fica parado, entao ele usa o enlace uma fatia a cada ida e volta inteira. Num
 * enlace com atraso de dez fatias, isso e menos de cinco por cento.
 */
public final class ParadaEEspera extends Protocolo {

    private int esperado;
    private int ultimoAceito = -1;

    @Override
    public String nome() {
        return "parada e espera";
    }

    @Override
    public int maiorJanela(int espaco) {
        return 1;
    }

    @Override
    protected void aoChegarDado(Quadro quadro, int fatia, Canal volta) {
        if (quadro.numero() == Math.floorMod(esperado, espaco)) {
            recebido.write(quadro.carga());

            ultimoAceito = quadro.numero();
            esperado++;
        }

        /* Confirma sempre, inclusive o repetido: a confirmacao perdida e o motivo de o
         * quadro ter voltado, e ficar calado faria os dois esperarem um ao outro. */
        volta.mandar(Quadro.confirmando(ultimoAceito < 0 ? espaco - 1 : ultimoAceito), fatia);
    }

    @Override
    protected void aoChegarConfirmacao(Quadro quadro) {
        if (quadro.numero() == Math.floorMod(base, espaco)) base++;
    }

    @Override
    protected int aMandar(int fatia) {
        if (base >= fluxo.length) return -1;

        if (mandadoEm[base] == Integer.MIN_VALUE / 2) return base;

        return esgotou(base, fatia) ? base : -1;
    }
}
