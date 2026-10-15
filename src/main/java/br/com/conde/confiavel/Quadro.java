package br.com.conde.confiavel;

/**
 * UM QUADRO NO FIO: um numero, um byte e um bit dizendo se e dado ou confirmacao.
 *
 * <p>O numero NAO e a posicao do byte no fluxo. Ele e a posicao modulo o espaco de
 * numeracao, e e essa diferenca que da assunto para o repositorio inteiro: o receptor ve
 * {@code 3} e precisa decidir se aquele e o terceiro byte, o decimo nono ou o centesimo
 * trigesimo quinto.
 *
 * <p>Com espaco de numeracao infinito nao haveria protocolo nenhum para estudar. Quadro tem
 * cabecalho de tamanho fixo, entao o numero cabe em poucos bits, e tudo o que vem depois
 * decorre disso.
 */
public record Quadro(int numero, byte carga, boolean confirmacao) {

    public static Quadro dado(int numero, byte carga) {
        return new Quadro(numero, carga, false);
    }

    public static Quadro confirmando(int numero) {
        return new Quadro(numero, (byte) 0, true);
    }

    @Override
    public String toString() {
        return (confirmacao ? "conf " : "dado ") + numero;
    }
}
