package br.com.conde.confiavel;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * O PROTOCOLO: o laco que todos compartilham, e os tres pontos em que eles diferem.
 *
 * <p>A cada fatia de tempo acontece sempre a mesma coisa, nesta ordem: o receptor olha o que
 * chegou e responde, o emissor olha as confirmacoes que chegaram, e o emissor manda um
 * quadro se tiver o que mandar.
 *
 * <p>Tudo o que distingue parada e espera, volta N e repeticao seletiva cabe em tres
 * perguntas: o que o receptor faz com um numero fora de ordem, o que a confirmacao diz, e o
 * que o emissor retransmite quando o tempo esgota.
 *
 * <h2>O contrato</h2>
 *
 * <p>O fluxo entregue ao destinatario tem de ser EXATAMENTE o fluxo que entrou, byte por
 * byte e na mesma ordem. Nao e "quase todos os bytes" nem "os bytes certos em alguma ordem".
 * Essa e a unica coisa que um transporte confiavel promete, e e o que o juiz confere.
 */
public abstract class Protocolo {

    /** O resultado de uma transmissao inteira. */
    public record Resultado(String protocolo, byte[] entregue, int fatias, long transmissoes,
            long retransmissoes, long confirmacoes, boolean terminou) {

        public boolean correto(byte[] original) {
            return java.util.Arrays.equals(entregue, original);
        }

        public double transmissoesPorByte() {
            return transmissoes / (double) entregue.length;
        }

        /** Bytes entregues por fatia de tempo, que e o uso do enlace. */
        public double uso() {
            return entregue.length / (double) fatias;
        }
    }

    protected int espaco;
    protected int janela;
    protected int tempoLimite;

    protected byte[] fluxo;

    /** O primeiro byte ainda nao confirmado. */
    protected int base;

    /** O proximo byte a mandar pela primeira vez. */
    protected int proximo;

    /** Em que fatia cada byte mandado comeca a contar o tempo. */
    protected int[] mandadoEm;

    protected final ByteArrayOutputStream recebido = new ByteArrayOutputStream();

    protected long transmissoes;
    protected long retransmissoes;
    protected long confirmacoes;

    public abstract String nome();

    /** A maior janela que este protocolo aguenta num espaco de numeracao deste tamanho. */
    public abstract int maiorJanela(int espaco);

    protected abstract void aoChegarDado(Quadro quadro, int fatia, Canal volta);

    protected abstract void aoChegarConfirmacao(Quadro quadro);

    /** Qual byte mandar nesta fatia, ou -1 para ficar calado. */
    protected abstract int aMandar(int fatia);

    protected void comecar() {
    }

    /**
     * Roda a transmissao inteira e devolve o que chegou do outro lado.
     *
     * <p>O orcamento de fatias existe porque um protocolo mal configurado pode nunca
     * terminar, e travar seria pior que devolver um fluxo incompleto: o juiz ve o fluxo
     * incompleto e acusa.
     */
    public Resultado entregar(byte[] fluxo, Canal ida, Canal volta, int espaco, int janela,
            int tempoLimite, int orcamento) {

        this.fluxo = fluxo;
        this.espaco = espaco;
        this.janela = janela;
        this.tempoLimite = tempoLimite;

        this.base = 0;
        this.proximo = 0;
        this.mandadoEm = new int[fluxo.length];

        java.util.Arrays.fill(mandadoEm, Integer.MIN_VALUE / 2);

        comecar();

        int fatia = 0;

        while (fatia < orcamento && base < fluxo.length) {
            for (Quadro quadro : ida.receber(fatia)) aoChegarDado(quadro, fatia, volta);

            for (Quadro quadro : volta.receber(fatia)) {
                confirmacoes++;

                aoChegarConfirmacao(quadro);
            }

            int qual = aMandar(fatia);

            if (qual >= 0) {
                boolean primeiraVez = mandadoEm[qual] == Integer.MIN_VALUE / 2;

                transmissoes++;

                if (!primeiraVez) retransmissoes++;

                mandadoEm[qual] = fatia;

                if (qual >= proximo) proximo = qual + 1;

                ida.mandar(Quadro.dado(qual % espaco, fluxo[qual]), fatia);
            }

            fatia++;
        }

        return new Resultado(nome(), recebido.toByteArray(), fatia, transmissoes,
                retransmissoes, confirmacoes, base >= fluxo.length);
    }

    /** O tempo esgotou para este byte? */
    protected boolean esgotou(int qual, int fatia) {
        return fatia - mandadoEm[qual] >= tempoLimite;
    }

    /** Dentro de uma janela que da a volta no espaco de numeracao. */
    protected static boolean dentroDaJanela(int numero, int inicio, int tamanho, int espaco) {
        return Math.floorMod(numero - inicio, espaco) < tamanho;
    }

    /** As tres implementacoes, na ordem das tabelas. */
    public static List<Protocolo> todos() {
        return List.of(new ParadaEEspera(), new VoltaN(), new RepeticaoSeletiva());
    }
}
