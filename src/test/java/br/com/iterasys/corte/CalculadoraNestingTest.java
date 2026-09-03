package br.com.iterasys.corte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraNestingTest {

    @Test
    void calculaFileirasEComprimentoNecessario() {
        Peca peca = new Peca("Gaxeta", 200, 100, 10);

        NestingResultado resultado = CalculadoraNesting.calcularAberturaNecessaria(1220, peca);

        assertEquals(6, resultado.pecasPorFileira());
        assertEquals(2, resultado.fileiras());
        assertEquals(200, resultado.comprimentoNecessarioMm());
        assertFalse(resultado.pecaGirada());
    }

    @Test
    void escolheOrientacaoGiradaQuandoGastaMenosMaterial() {
        // sem girar: pecasPorFileira=floor(1220/700)=1, fileiras=10, comprimento=2000mm
        // girada (200x700): pecasPorFileira=floor(1220/200)=6, fileiras=2, comprimento=1400mm
        Peca peca = new Peca("Suporte", 700, 200, 10);

        NestingResultado resultado = CalculadoraNesting.calcularAberturaNecessaria(1220, peca);

        assertTrue(resultado.pecaGirada());
        assertEquals(6, resultado.pecasPorFileira());
        assertEquals(2, resultado.fileiras());
        assertEquals(1400, resultado.comprimentoNecessarioMm());
    }

    @Test
    void pecaMaiorQueLarguraDisponivelEmQualquerOrientacaoLancaExcecao() {
        Peca peca = new Peca("Gaxeta grande", 1500, 1600, 1);

        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraNesting.calcularAberturaNecessaria(1220, peca));
    }

    @Test
    void pecaQueSoCabeGiradaUsaRotacao() {
        Peca peca = new Peca("Painel", 1500, 100, 1);

        NestingResultado resultado = CalculadoraNesting.calcularAberturaNecessaria(1220, peca);

        assertTrue(resultado.pecaGirada());
        assertEquals(12, resultado.pecasPorFileira());
    }

    @Test
    void calculaCapacidadeLimitadaPorAreaFixa() {
        Peca peca = new Peca("Gaxeta", 200, 100, 1000);

        EncaixeEmArea encaixe = CalculadoraNesting.calcularMelhorEncaixeEmArea(1400, 1220, peca);

        assertEquals(84, encaixe.capacidadeTotal());
    }
}
