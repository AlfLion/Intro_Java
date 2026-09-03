package br.com.iterasys.corte;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Menu de console para calcular o plano de corte de uma peça, seja com um único
 * material ou com um material conjugado (dois materiais colados, ex.: Poron + cola
 * transfer), sem precisar editar código.
 */
public class OrdemServicoConsole {

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        boolean continuar = true;

        while (continuar) {
            System.out.println();
            System.out.println("=== Cálculo de plano de corte ===");
            System.out.println("1 - Peça com um único material");
            System.out.println("2 - Peça com material conjugado (dois materiais colados)");
            System.out.println("0 - Sair");
            System.out.print("Escolha uma opção: ");

            String opcao = scanner.nextLine().trim();
            switch (opcao) {
                case "1" -> calcularMaterialUnico(scanner);
                case "2" -> calcularMaterialConjugado(scanner);
                case "0" -> continuar = false;
                default -> System.out.println("Opção inválida.");
            }
        }

        scanner.close();
        System.out.println("Até mais!");
    }

    private static void calcularMaterialUnico(Scanner scanner) {
        double largura = lerDouble(scanner, "Largura do material (mm): ");
        Peca peca = lerPeca(scanner);

        try {
            NestingResultado resultado = CalculadoraNesting.calcularAberturaNecessaria(largura, peca);
            System.out.println();
            System.out.println("Peças por fileira: " + resultado.pecasPorFileira());
            System.out.println("Fileiras necessárias: " + resultado.fileiras());
            System.out.println("Comprimento a abrir do material: " + resultado.comprimentoNecessarioMm() + "mm");
        } catch (IllegalArgumentException e) {
            System.out.println("Não foi possível calcular: " + e.getMessage());
        }
    }

    private static void calcularMaterialConjugado(Scanner scanner) {
        System.out.println("-- Material A --");
        String nomeA = lerTexto(scanner, "Nome (ex.: Poron): ");
        double larguraA = lerDouble(scanner, "Largura (mm): ");

        System.out.println("-- Material B --");
        String nomeB = lerTexto(scanner, "Nome (ex.: Cola transfer): ");
        double larguraB = lerDouble(scanner, "Largura (mm): ");

        Peca peca = lerPeca(scanner);

        try {
            MaterialConjugado material = new MaterialConjugado(new Material(nomeA, larguraA), new Material(nomeB, larguraB));
            PlanoConjugado plano = CalculadoraMaterialConjugado.calcular(material, peca);
            imprimirPlano(material, plano);
        } catch (IllegalArgumentException e) {
            System.out.println("Não foi possível calcular: " + e.getMessage());
        }
    }

    private static void imprimirPlano(MaterialConjugado material, PlanoConjugado plano) {
        System.out.println();
        System.out.println("Material mais largo: " + material.getMaterialMaisLargo());
        System.out.println("Material mais estreito: " + material.getMaterialMaisEstreito());
        System.out.println();
        System.out.println("Placas cheias (" + plano.larguraPlacaCheiaMm() + "x" + plano.comprimentoPlacaCheiaMm()
                + "mm, sem sobra): " + plano.placasCheias());

        if (plano.temTiraComplementar()) {
            TiraComplementar tira = plano.tiraComplementar();
            System.out.println("Tira complementar: " + tira.larguraMm() + "x" + tira.comprimentoMm()
                    + "mm (" + tira.quantidadePecas() + " peças)");

            SobraMaterial sobra = plano.sobra();
            System.out.println("SOBRA: " + sobra.material().getNome() + " " + sobra.larguraMm()
                    + "x" + sobra.comprimentoMm() + "mm (não aproveitável para esta peça)");
        } else {
            System.out.println("Quantidade fechou exatamente nas placas cheias, sem sobra.");
        }
    }

    private static Peca lerPeca(Scanner scanner) {
        System.out.println("-- Peça --");
        String nome = lerTexto(scanner, "Nome: ");
        double largura = lerDouble(scanner, "Largura (mm): ");
        double comprimento = lerDouble(scanner, "Comprimento (mm): ");
        int quantidade = lerInt(scanner, "Quantidade demandada: ");
        return new Peca(nome.isBlank() ? "Peça" : nome, largura, comprimento, quantidade);
    }

    private static String lerTexto(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static double lerDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String entrada = scanner.nextLine().trim().replace(",", ".");
            try {
                return Double.parseDouble(entrada);
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido, digite um número (ex.: 1400 ou 1400,5).");
            }
        }
    }

    private static int lerInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String entrada = scanner.nextLine().trim();
            try {
                return Integer.parseInt(entrada);
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido, digite um número inteiro.");
            }
        }
    }
}
