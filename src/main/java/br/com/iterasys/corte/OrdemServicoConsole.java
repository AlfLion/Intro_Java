package br.com.iterasys.corte;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
            System.out.println("1 - Peça avulsa com um único material");
            System.out.println("2 - Peça avulsa com material conjugado (dois materiais colados)");
            System.out.println("3 - Ordem de serviço (várias peças)");
            System.out.println("0 - Sair");
            System.out.print("Escolha uma opção: ");

            String opcao = scanner.nextLine().trim();
            switch (opcao) {
                case "1" -> calcularMaterialUnico(scanner);
                case "2" -> calcularMaterialConjugado(scanner);
                case "3" -> montarOrdemServico(scanner);
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
            System.out.println("Peças por fileira: " + resultado.pecasPorFileira() + textoGirada(resultado.pecaGirada()));
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
                + "mm, sem sobra): " + plano.placasCheias() + (plano.placasCheias() > 0 ? textoGirada(plano.placaGirada()) : ""));

        if (plano.temTiraComplementar()) {
            TiraComplementar tira = plano.tiraComplementar();
            System.out.println("Tira complementar: " + tira.larguraMm() + "x" + tira.comprimentoMm()
                    + "mm (" + tira.quantidadePecas() + " peças)" + textoGirada(tira.pecaGirada()));

            SobraMaterial sobra = plano.sobra();
            System.out.println("SOBRA: " + sobra.material().getNome() + " " + sobra.larguraMm()
                    + "x" + sobra.comprimentoMm() + "mm (não aproveitável para esta peça)");
        } else {
            System.out.println("Quantidade fechou exatamente nas placas cheias, sem sobra.");
        }
    }

    private static void montarOrdemServico(Scanner scanner) {
        String numero = lerTexto(scanner, "Número da ordem de serviço: ");
        OrdemServico ordem = new OrdemServico(numero.isBlank() ? "OS" : numero);

        List<Material> materiaisUnicosCadastrados = new ArrayList<>();
        List<MaterialConjugado> materiaisConjugadosCadastrados = new ArrayList<>();

        boolean adicionandoItens = true;
        while (adicionandoItens) {
            System.out.println();
            System.out.println("-- Item " + (ordem.getItens().size() + 1) + " --");
            System.out.println("1 - Peça com material único");
            System.out.println("2 - Peça com material conjugado");
            System.out.println("0 - Finalizar itens e calcular a ordem");
            System.out.print("Escolha uma opção: ");
            String opcao = scanner.nextLine().trim();

            switch (opcao) {
                case "1" -> {
                    Material material = escolherOuCadastrarMaterialUnico(scanner, materiaisUnicosCadastrados);
                    ordem.adicionarItem(ItemOrdemServico.deMaterialUnico(lerPeca(scanner), material));
                }
                case "2" -> {
                    MaterialConjugado material = escolherOuCadastrarMaterialConjugado(scanner, materiaisConjugadosCadastrados);
                    ordem.adicionarItem(ItemOrdemServico.deMaterialConjugado(lerPeca(scanner), material));
                }
                case "0" -> adicionandoItens = false;
                default -> System.out.println("Opção inválida.");
            }
        }

        if (ordem.getItens().isEmpty()) {
            System.out.println("Nenhum item adicionado, ordem cancelada.");
            return;
        }

        try {
            ResumoOrdemServico resumo = CalculadoraOrdemServico.calcular(ordem);
            imprimirResumoOrdemServico(ordem, resumo);
        } catch (IllegalArgumentException e) {
            System.out.println("Não foi possível calcular a ordem: " + e.getMessage());
        }
    }

    private static Material escolherOuCadastrarMaterialUnico(Scanner scanner, List<Material> cadastrados) {
        if (!cadastrados.isEmpty()) {
            System.out.println("Materiais já usados nesta ordem:");
            for (int i = 0; i < cadastrados.size(); i++) {
                System.out.println((i + 1) + " - " + cadastrados.get(i));
            }
            System.out.println("0 - Cadastrar novo material");
            int escolha = lerInt(scanner, "Escolha: ");
            if (escolha >= 1 && escolha <= cadastrados.size()) {
                return cadastrados.get(escolha - 1);
            }
        }
        String nome = lerTexto(scanner, "Nome do material: ");
        double largura = lerDouble(scanner, "Largura (mm): ");
        Material material = new Material(nome.isBlank() ? "Material" : nome, largura);
        cadastrados.add(material);
        return material;
    }

    private static MaterialConjugado escolherOuCadastrarMaterialConjugado(Scanner scanner, List<MaterialConjugado> cadastrados) {
        if (!cadastrados.isEmpty()) {
            System.out.println("Combinações de material conjugado já usadas nesta ordem:");
            for (int i = 0; i < cadastrados.size(); i++) {
                MaterialConjugado mc = cadastrados.get(i);
                System.out.println((i + 1) + " - " + mc.getMaterialMaisLargo().getNome() + " + " + mc.getMaterialMaisEstreito().getNome());
            }
            System.out.println("0 - Cadastrar nova combinação");
            int escolha = lerInt(scanner, "Escolha: ");
            if (escolha >= 1 && escolha <= cadastrados.size()) {
                return cadastrados.get(escolha - 1);
            }
        }
        System.out.println("-- Material A --");
        String nomeA = lerTexto(scanner, "Nome (ex.: Poron): ");
        double larguraA = lerDouble(scanner, "Largura (mm): ");
        System.out.println("-- Material B --");
        String nomeB = lerTexto(scanner, "Nome (ex.: Cola transfer): ");
        double larguraB = lerDouble(scanner, "Largura (mm): ");
        MaterialConjugado material = new MaterialConjugado(new Material(nomeA, larguraA), new Material(nomeB, larguraB));
        cadastrados.add(material);
        return material;
    }

    private static void imprimirResumoOrdemServico(OrdemServico ordem, ResumoOrdemServico resumo) {
        System.out.println();
        System.out.println("=== Resumo da Ordem de Serviço " + ordem.getNumero() + " ===");

        for (ResultadoItem resultado : resumo.resultadosPorItem()) {
            Peca peca = resultado.item().getPeca();
            System.out.println();
            System.out.println("Peça: " + peca.getNome() + " " + peca.getLarguraMm() + "x" + peca.getComprimentoMm()
                    + "mm, quantidade " + peca.getQuantidade());

            if (resultado.item().isMaterialConjugado()) {
                PlanoConjugado plano = resultado.planoConjugado();
                System.out.println("  Placas cheias: " + plano.placasCheias()
                        + (plano.placasCheias() > 0 ? textoGirada(plano.placaGirada()) : ""));
                if (plano.temTiraComplementar()) {
                    TiraComplementar tira = plano.tiraComplementar();
                    SobraMaterial sobra = plano.sobra();
                    System.out.println("  Tira complementar: " + tira.larguraMm() + "x" + tira.comprimentoMm()
                            + "mm (" + tira.quantidadePecas() + " peças)" + textoGirada(tira.pecaGirada()));
                    System.out.println("  SOBRA: " + sobra.material().getNome() + " " + sobra.larguraMm()
                            + "x" + sobra.comprimentoMm() + "mm");
                }
            } else {
                NestingResultado nesting = resultado.nestingMaterialUnico();
                System.out.println("  Material: " + resultado.item().getMaterialUnico().getNome());
                System.out.println("  Comprimento a abrir: " + nesting.comprimentoNecessarioMm() + "mm" + textoGirada(nesting.pecaGirada()));
            }
        }

        System.out.println();
        System.out.println("-- Consumo total de material nesta ordem --");
        resumo.consumoTotalPorMaterialMm().forEach((nome, comprimentoMm) ->
                System.out.println(nome + ": " + comprimentoMm + "mm (" + (comprimentoMm / 1000.0) + "m)"));

        if (!resumo.sobras().isEmpty()) {
            System.out.println();
            System.out.println("-- Sobras geradas --");
            for (SobraMaterial sobra : resumo.sobras()) {
                System.out.println(sobra.material().getNome() + ": " + sobra.larguraMm() + "x" + sobra.comprimentoMm() + "mm");
            }
        }
    }

    private static String textoGirada(boolean girada) {
        return girada ? " (peça girada 90°)" : "";
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
