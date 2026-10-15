package br.com.conde.confiavel;

import java.util.ArrayList;
import java.util.List;

/**
 * O CANAL: ele perde, atrasa e desordena, e nao avisa nada a ninguem.
 *
 * <p>O tempo anda em FATIAS. Cada fatia e uma oportunidade de transmitir um quadro, nos dois
 * sentidos. Um quadro entregue na fatia t chega na fatia {@code t + atraso + sacudida}, onde
 * a sacudida e sorteada, e e dela que sai a chegada fora de ordem.
 *
 * <p>O canal nao tem memoria de quem mandou o que, nao verifica nada e nao reordena de volta.
 * Tudo o que parece ordem do outro lado foi o protocolo que construiu.
 *
 * <h2>Por que a sacudida importa</h2>
 *
 * <p>Sem ela, um quadro mandado antes sempre chega antes, e o receptor nunca ve um numero
 * fora de ordem que nao seja retransmissao. A repeticao seletiva existe exatamente para
 * guardar o que chega fora de ordem; medir sem sacudida seria medir o caso em que ela nao
 * serve para nada.
 */
public final class Canal {

    /** Sacudida de uma fatia so quer dizer canal que preserva a ordem. */
    public static final int EM_ORDEM = 1;

    /** A sacudida que desordena: 0, 1, 2 ou 3 fatias a mais, sorteadas. */
    public static final int DESORDENA = 4;

    private final int atraso;
    private final double perda;
    private final int sacudida;
    private final Sorteio sorteio;

    private final List<Quadro> emVoo = new ArrayList<>();
    private final List<Integer> chegadas = new ArrayList<>();

    private long entregues;
    private long perdidos;

    public Canal(int atraso, double perda, int sacudida, Sorteio sorteio) {
        if (sacudida < 1) throw new IllegalArgumentException("sacudida minima e 1");

        this.atraso = atraso;
        this.perda = perda;
        this.sacudida = sacudida;
        this.sorteio = sorteio;
    }

    /** Um canal que entrega na ordem em que recebeu, que e o caso dos livros. */
    public static Canal emOrdem(int atraso, double perda, Sorteio sorteio) {
        return new Canal(atraso, perda, EM_ORDEM, sorteio);
    }

    /** Um canal que desordena, que e o caso de uma rede com varios caminhos. */
    public static Canal desordenando(int atraso, double perda, Sorteio sorteio) {
        return new Canal(atraso, perda, DESORDENA, sorteio);
    }

    public int sacudida() {
        return sacudida;
    }

    public int atraso() {
        return atraso;
    }

    /** Poe um quadro no fio na fatia dada. Ele pode simplesmente sumir. */
    public void mandar(Quadro quadro, int fatia) {
        if (sorteio.com(perda)) {
            perdidos++;
            return;
        }

        entregues++;

        emVoo.add(quadro);
        chegadas.add(fatia + atraso + sorteio.ate(sacudida));
    }

    /** Os quadros que chegam nesta fatia, na ordem em que foram postos no fio. */
    public List<Quadro> receber(int fatia) {
        List<Quadro> saida = new ArrayList<>();

        for (int i = emVoo.size() - 1; i >= 0; i--) {
            if (chegadas.get(i) > fatia) continue;

            saida.add(0, emVoo.remove(i));
            chegadas.remove(i);
        }

        return saida;
    }

    public long entregues() {
        return entregues;
    }

    public long perdidos() {
        return perdidos;
    }

    public boolean vazio() {
        return emVoo.isEmpty();
    }
}
